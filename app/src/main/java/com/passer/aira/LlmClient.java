package com.passer.aira;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class LlmClient {
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 180_000;
    private volatile HttpURLConnection activeConnection;

    String complete(ModelConfig config, String systemPrompt, List<ChatMessage> history)
            throws IOException, JSONException {
        if (config.apiKey.isEmpty()) {
            throw new IOException("请先在设置中填写 API Key。");
        }

        boolean anthropic = ModelConfig.ANTHROPIC.equals(config.provider);
        URL endpoint = new URL(anthropic
                ? "https://api.anthropic.com/v1/messages"
                : ModelConfig.OPENAI.equals(config.provider)
                ? "https://api.openai.com/v1/chat/completions"
                : "https://api.deepseek.com/chat/completions");

        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        activeConnection = connection;
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (anthropic) {
                connection.setRequestProperty("x-api-key", config.apiKey);
                connection.setRequestProperty("anthropic-version", "2023-06-01");
            } else {
                connection.setRequestProperty("Authorization", "Bearer " + config.apiKey);
            }

            JSONObject body = anthropic
                    ? anthropicBody(config, systemPrompt, history)
                    : openAiBody(config, systemPrompt, history);
            byte[] encoded = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(encoded.length);
            try (OutputStream stream = connection.getOutputStream()) {
                stream.write(encoded);
            }

            int status = connection.getResponseCode();
            InputStream responseStream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String response = readAll(responseStream);
            if (status < 200 || status >= 300) {
                throw new IOException("模型接口返回 HTTP " + status + "：" + extractError(response));
            }
            JSONObject result = new JSONObject(response);
            if (anthropic) {
                JSONArray parts = result.optJSONArray("content");
                StringBuilder text = new StringBuilder();
                if (parts != null) {
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject part = parts.optJSONObject(i);
                        if (part != null && "text".equals(part.optString("type"))) {
                            text.append(part.optString("text"));
                        }
                    }
                }
                return text.toString().trim();
            }
            JSONArray choices = result.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new IOException("模型没有返回可用内容。");
            }
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            return message == null ? "" : message.optString("content", "").trim();
        } finally {
            if (activeConnection == connection) {
                activeConnection = null;
            }
            connection.disconnect();
        }
    }

    void cancelActive() {
        HttpURLConnection connection = activeConnection;
        if (connection != null) {
            connection.disconnect();
        }
    }

    private JSONObject openAiBody(
            ModelConfig config,
            String systemPrompt,
            List<ChatMessage> history
    ) throws JSONException {
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        appendHistory(messages, history);
        return new JSONObject()
                .put("model", config.model)
                .put("messages", messages)
                .put("max_tokens", 4096)
                .put("temperature", 0.2);
    }

    private JSONObject anthropicBody(
            ModelConfig config,
            String systemPrompt,
            List<ChatMessage> history
    ) throws JSONException {
        JSONArray messages = new JSONArray();
        appendHistory(messages, history);
        return new JSONObject()
                .put("model", config.model)
                .put("system", systemPrompt)
                .put("messages", messages)
                .put("max_tokens", 4096);
    }

    private void appendHistory(JSONArray output, List<ChatMessage> history) throws JSONException {
        int start = Math.max(0, history.size() - 40);
        for (int i = start; i < history.size(); i++) {
            ChatMessage message = history.get(i);
            output.put(new JSONObject()
                    .put("role", message.role)
                    .put("content", message.content));
        }
    }

    private String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                value.append(line);
            }
        }
        return value.toString();
    }

    private String extractError(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "无错误详情";
        }
        try {
            JSONObject body = new JSONObject(raw);
            Object error = body.opt("error");
            if (error instanceof JSONObject) {
                String message = ((JSONObject) error).optString("message");
                if (!message.isEmpty()) {
                    return message;
                }
            }
            return String.valueOf(error);
        } catch (JSONException ignored) {
            return raw.length() > 500 ? raw.substring(0, 500) : raw;
        }
    }
}
