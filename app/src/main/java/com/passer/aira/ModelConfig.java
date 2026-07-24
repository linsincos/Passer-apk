package com.passer.aira;

import java.util.Locale;

final class ModelConfig {
    static final String DEEPSEEK = "deepseek";
    static final String OPENAI = "openai";
    static final String ANTHROPIC = "anthropic";

    final String provider;
    final String model;
    final String apiKey;

    ModelConfig(String provider, String model, String apiKey) {
        this.provider = normalizeProvider(provider);
        this.model = model == null || model.trim().isEmpty()
                ? defaultModel(this.provider)
                : model.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    static String normalizeProvider(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (OPENAI.equals(key) || ANTHROPIC.equals(key)) {
            return key;
        }
        return DEEPSEEK;
    }

    static String defaultModel(String provider) {
        switch (normalizeProvider(provider)) {
            case OPENAI:
                return "gpt-4.1-mini";
            case ANTHROPIC:
                return "claude-sonnet-4-6";
            default:
                return "deepseek-chat";
        }
    }

    static String displayName(String provider) {
        switch (normalizeProvider(provider)) {
            case OPENAI:
                return "OpenAI";
            case ANTHROPIC:
                return "Anthropic";
            default:
                return "DeepSeek";
        }
    }
}
