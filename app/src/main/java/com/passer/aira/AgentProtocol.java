package com.passer.aira;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AgentProtocol {
    private static final int MAX_ACTIONS = 8;
    private static final Pattern ACTION_BLOCK = Pattern.compile(
            "\\[\\[AIRA_ACTION\\]\\](.*?)\\[\\[/AIRA_ACTION\\]\\]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    static final class ParsedReply {
        final String visibleText;
        final List<JSONObject> actions;

        ParsedReply(String visibleText, List<JSONObject> actions) {
            this.visibleText = visibleText;
            this.actions = Collections.unmodifiableList(actions);
        }
    }

    ParsedReply parse(String reply) throws JSONException {
        String raw = reply == null ? "" : reply;
        Matcher matcher = ACTION_BLOCK.matcher(raw);
        List<JSONObject> actions = new ArrayList<>();
        while (matcher.find() && actions.size() < MAX_ACTIONS) {
            parsePayload(matcher.group(1).trim(), actions);
        }
        String visible = ACTION_BLOCK.matcher(raw).replaceAll("").trim();
        return new ParsedReply(visible, actions);
    }

    private void parsePayload(String payload, List<JSONObject> actions) throws JSONException {
        if (payload.isEmpty()) {
            return;
        }
        if (payload.startsWith("[")) {
            JSONArray array = new JSONArray(payload);
            for (int i = 0; i < array.length() && actions.size() < MAX_ACTIONS; i++) {
                JSONObject action = array.optJSONObject(i);
                if (action != null) {
                    requireActionName(action);
                    actions.add(action);
                }
            }
            return;
        }
        JSONObject action = new JSONObject(payload);
        requireActionName(action);
        actions.add(action);
    }

    private void requireActionName(JSONObject action) throws JSONException {
        if (action.optString("action").trim().isEmpty()) {
            throw new JSONException("手机动作缺少 action 字段。");
        }
    }
}
