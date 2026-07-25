package com.passer.aira;

import org.json.JSONException;
import org.json.JSONObject;

final class AttachmentData {
    final String name;
    final String mimeType;
    final long size;
    final String textContent;
    final String base64Data;

    private AttachmentData(
            String name,
            String mimeType,
            long size,
            String textContent,
            String base64Data
    ) {
        this.name = name == null || name.trim().isEmpty() ? "附件" : name.trim();
        this.mimeType = mimeType == null || mimeType.trim().isEmpty()
                ? "application/octet-stream"
                : mimeType.trim();
        this.size = Math.max(0, size);
        this.textContent = textContent == null ? "" : textContent;
        this.base64Data = base64Data == null ? "" : base64Data;
    }

    static AttachmentData image(String name, String mimeType, long size, String base64Data) {
        return new AttachmentData(name, mimeType, size, "", base64Data);
    }

    static AttachmentData text(String name, String mimeType, long size, String textContent) {
        return new AttachmentData(name, mimeType, size, textContent, "");
    }

    static AttachmentData metadata(String name, String mimeType, long size) {
        return new AttachmentData(name, mimeType, size, "", "");
    }

    boolean isImage() {
        return mimeType.startsWith("image/");
    }

    boolean hasImageData() {
        return isImage() && !base64Data.isEmpty();
    }

    boolean hasTextContent() {
        return !textContent.isEmpty();
    }

    JSONObject toMetadataJson() throws JSONException {
        return new JSONObject()
                .put("name", name)
                .put("mime_type", mimeType)
                .put("size", size);
    }

    static AttachmentData fromMetadataJson(JSONObject object) {
        return metadata(
                object.optString("name", "附件"),
                object.optString("mime_type", "application/octet-stream"),
                object.optLong("size", 0)
        );
    }
}
