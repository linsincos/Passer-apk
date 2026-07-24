package com.passer.aira;

import org.json.JSONException;
import org.json.JSONObject;

final class ChatMessage {
    final String role;
    final String content;
    final long createdAt;

    ChatMessage(String role, String content) {
        this(role, content, System.currentTimeMillis());
    }

    ChatMessage(String role, String content, long createdAt) {
        this.role = "assistant".equals(role) ? "assistant" : "user";
        this.content = content == null ? "" : content;
        this.createdAt = createdAt;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("role", role)
                .put("content", content)
                .put("created_at", createdAt);
    }

    static ChatMessage fromJson(JSONObject object) {
        return new ChatMessage(
                object.optString("role", "user"),
                object.optString("content", ""),
                object.optLong("created_at", System.currentTimeMillis())
        );
    }
}
