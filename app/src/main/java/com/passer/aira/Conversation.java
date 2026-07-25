package com.passer.aira;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

final class Conversation {
    static final String DEFAULT_TITLE = "新对话";
    private static final int MAX_TITLE_LENGTH = 28;

    final String id;
    final String title;
    final long updatedAt;
    final List<ChatMessage> messages;

    Conversation(String id, String title, long updatedAt, List<ChatMessage> messages) {
        this.id = id == null || id.trim().isEmpty()
                ? UUID.randomUUID().toString()
                : id.trim();
        this.title = normalizeTitle(title);
        this.updatedAt = updatedAt;
        this.messages = Collections.unmodifiableList(new ArrayList<>(
                messages == null ? Collections.emptyList() : messages
        ));
    }

    static Conversation empty() {
        return new Conversation(
                UUID.randomUUID().toString(),
                DEFAULT_TITLE,
                System.currentTimeMillis(),
                Collections.emptyList()
        );
    }

    static String titleFromMessages(List<ChatMessage> messages) {
        if (messages != null) {
            for (ChatMessage message : messages) {
                if ("user".equals(message.role) && !message.content.trim().isEmpty()) {
                    return normalizeTitle(message.content);
                }
            }
        }
        return DEFAULT_TITLE;
    }

    static String normalizeTitle(String value) {
        String clean = value == null ? "" : value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim()
                .replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            return DEFAULT_TITLE;
        }
        if (clean.length() > MAX_TITLE_LENGTH) {
            return clean.substring(0, MAX_TITLE_LENGTH - 1) + "…";
        }
        return clean;
    }

    JSONObject toJson(int maxMessages) throws JSONException {
        JSONArray values = new JSONArray();
        int start = Math.max(0, messages.size() - maxMessages);
        for (int i = start; i < messages.size(); i++) {
            values.put(messages.get(i).toJson());
        }
        return new JSONObject()
                .put("id", id)
                .put("title", title)
                .put("updated_at", updatedAt)
                .put("messages", values);
    }

    static Conversation fromJson(JSONObject object) {
        List<ChatMessage> values = new ArrayList<>();
        JSONArray messages = object.optJSONArray("messages");
        if (messages != null) {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject message = messages.optJSONObject(i);
                if (message != null) {
                    values.add(ChatMessage.fromJson(message));
                }
            }
        }
        return new Conversation(
                object.optString("id", ""),
                object.optString("title", titleFromMessages(values)),
                object.optLong("updated_at", System.currentTimeMillis()),
                values
        );
    }
}
