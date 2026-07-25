package com.passer.aira;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ChatMessage {
    final String role;
    final String content;
    final long createdAt;
    final List<AttachmentData> attachments;

    ChatMessage(String role, String content) {
        this(role, content, System.currentTimeMillis(), Collections.emptyList());
    }

    ChatMessage(String role, String content, List<AttachmentData> attachments) {
        this(role, content, System.currentTimeMillis(), attachments);
    }

    ChatMessage(String role, String content, long createdAt) {
        this(role, content, createdAt, Collections.emptyList());
    }

    ChatMessage(
            String role,
            String content,
            long createdAt,
            List<AttachmentData> attachments
    ) {
        this.role = "assistant".equals(role) ? "assistant" : "user";
        this.content = content == null ? "" : content;
        this.createdAt = createdAt;
        this.attachments = Collections.unmodifiableList(new ArrayList<>(
                attachments == null ? Collections.emptyList() : attachments
        ));
    }

    JSONObject toJson() throws JSONException {
        JSONObject value = new JSONObject()
                .put("role", role)
                .put("content", content)
                .put("created_at", createdAt);
        if (!attachments.isEmpty()) {
            JSONArray metadata = new JSONArray();
            for (AttachmentData attachment : attachments) {
                metadata.put(attachment.toMetadataJson());
            }
            value.put("attachments", metadata);
        }
        return value;
    }

    static ChatMessage fromJson(JSONObject object) {
        List<AttachmentData> attachments = new ArrayList<>();
        JSONArray metadata = object.optJSONArray("attachments");
        if (metadata != null) {
            for (int i = 0; i < metadata.length(); i++) {
                JSONObject item = metadata.optJSONObject(i);
                if (item != null) {
                    attachments.add(AttachmentData.fromMetadataJson(item));
                }
            }
        }
        return new ChatMessage(
                object.optString("role", "user"),
                object.optString("content", ""),
                object.optLong("created_at", System.currentTimeMillis()),
                attachments
        );
    }
}
