package com.passer.aira;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

final class AppStorage {
    private static final String PREFS = "aira_app";
    private static final String HISTORY = "history";
    private static final String PROVIDER = "provider";
    private static final String MODEL = "model";
    private static final String MEMORY = "memory";
    private static final String DISCLOSURE = "disclosure_accepted";
    private static final int MAX_MESSAGES = 60;

    private final SharedPreferences preferences;
    private final SecureStore secureStore;

    AppStorage(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        secureStore = new SecureStore(context);
    }

    ModelConfig loadConfig() {
        String provider = preferences.getString(PROVIDER, ModelConfig.DEEPSEEK);
        String model = preferences.getString(MODEL, ModelConfig.defaultModel(provider));
        return new ModelConfig(provider, model, secureStore.loadApiKey());
    }

    void saveConfig(String provider, String model, String apiKey, boolean replaceKey) throws Exception {
        String normalized = ModelConfig.normalizeProvider(provider);
        preferences.edit()
                .putString(PROVIDER, normalized)
                .putString(MODEL, model == null || model.trim().isEmpty()
                        ? ModelConfig.defaultModel(normalized)
                        : model.trim())
                .apply();
        if (replaceKey) {
            secureStore.saveApiKey(apiKey == null ? "" : apiKey.trim());
        }
    }

    void clearApiKey() {
        secureStore.clearApiKey();
    }

    List<ChatMessage> loadHistory() {
        List<ChatMessage> result = new ArrayList<>();
        String raw = preferences.getString(HISTORY, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                result.add(ChatMessage.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(HISTORY).apply();
        }
        return result;
    }

    void saveHistory(List<ChatMessage> source) {
        JSONArray array = new JSONArray();
        int start = Math.max(0, source.size() - MAX_MESSAGES);
        for (int i = start; i < source.size(); i++) {
            try {
                array.put(source.get(i).toJson());
            } catch (JSONException ignored) {
                // JSONObject with primitive fields should not fail.
            }
        }
        preferences.edit().putString(HISTORY, array.toString()).apply();
    }

    String loadMemory() {
        return preferences.getString(MEMORY, "");
    }

    void appendMemory(String note) {
        String clean = note == null ? "" : note.trim();
        if (clean.isEmpty()) {
            return;
        }
        String current = loadMemory();
        String combined = current == null || current.trim().isEmpty()
                ? clean
                : current.trim() + "\n- " + clean;
        if (combined.length() > 8000) {
            combined = combined.substring(combined.length() - 8000);
        }
        preferences.edit().putString(MEMORY, combined).apply();
    }

    void clearMemory() {
        preferences.edit().remove(MEMORY).apply();
    }

    boolean disclosureAccepted() {
        return preferences.getBoolean(DISCLOSURE, false);
    }

    void acceptDisclosure() {
        preferences.edit().putBoolean(DISCLOSURE, true).apply();
    }

    void clearHistory() {
        preferences.edit().remove(HISTORY).apply();
    }
}
