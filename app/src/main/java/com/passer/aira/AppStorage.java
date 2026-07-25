package com.passer.aira;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class AppStorage {
    private static final String PREFS = "aira_app";
    private static final String HISTORY = "history";
    private static final String CONVERSATIONS = "conversations_v2";
    private static final String CURRENT_CONVERSATION = "current_conversation";
    private static final String PROVIDER = "provider";
    private static final String MODEL = "model";
    private static final String MEMORY = "memory";
    private static final String DISCLOSURE = "disclosure_accepted";
    private static final int MAX_MESSAGES = 60;
    private static final int MAX_CONVERSATIONS = 50;

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

    Conversation loadActiveConversation() {
        List<Conversation> conversations = loadConversations();
        String currentId = preferences.getString(CURRENT_CONVERSATION, "");
        for (Conversation conversation : conversations) {
            if (conversation.id.equals(currentId)) {
                return conversation;
            }
        }
        if (!conversations.isEmpty()) {
            Conversation latest = conversations.get(0);
            preferences.edit().putString(CURRENT_CONVERSATION, latest.id).apply();
            return latest;
        }

        List<ChatMessage> legacy = loadLegacyHistory();
        Conversation first = new Conversation(
                null,
                Conversation.titleFromMessages(legacy),
                System.currentTimeMillis(),
                legacy
        );
        saveConversation(first);
        preferences.edit().remove(HISTORY).apply();
        return first;
    }

    List<Conversation> loadConversations() {
        List<Conversation> result = new ArrayList<>();
        String raw = preferences.getString(CONVERSATIONS, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                if (array.optJSONObject(i) != null) {
                    result.add(Conversation.fromJson(array.optJSONObject(i)));
                }
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(CONVERSATIONS).apply();
        }
        result.sort(Comparator.comparingLong(
                (Conversation conversation) -> conversation.updatedAt
        ).reversed());
        return result;
    }

    Conversation startConversation() {
        Conversation conversation = Conversation.empty();
        saveConversation(conversation);
        return conversation;
    }

    void saveConversation(String id, String title, List<ChatMessage> messages) {
        saveConversation(new Conversation(
                id,
                title,
                System.currentTimeMillis(),
                messages
        ));
    }

    private void saveConversation(Conversation conversation) {
        List<Conversation> conversations = loadConversations();
        for (int i = conversations.size() - 1; i >= 0; i--) {
            if (conversations.get(i).id.equals(conversation.id)) {
                conversations.remove(i);
            }
        }
        conversations.add(conversation);
        conversations.sort(Comparator.comparingLong(
                (Conversation value) -> value.updatedAt
        ).reversed());
        if (conversations.size() > MAX_CONVERSATIONS) {
            conversations = new ArrayList<>(
                    conversations.subList(0, MAX_CONVERSATIONS)
            );
        }
        persistConversations(conversations, conversation.id);
    }

    void renameConversation(String id, String title) {
        List<Conversation> conversations = loadConversations();
        for (int i = 0; i < conversations.size(); i++) {
            Conversation existing = conversations.get(i);
            if (existing.id.equals(id)) {
                conversations.set(i, new Conversation(
                        existing.id,
                        title,
                        System.currentTimeMillis(),
                        existing.messages
                ));
                persistConversations(conversations, id);
                return;
            }
        }
    }

    void deleteConversation(String id) {
        List<Conversation> conversations = loadConversations();
        conversations.removeIf(conversation -> conversation.id.equals(id));
        if (conversations.isEmpty()) {
            Conversation empty = Conversation.empty();
            conversations.add(empty);
            persistConversations(conversations, empty.id);
            return;
        }
        String active = preferences.getString(CURRENT_CONVERSATION, "");
        String nextActive = id.equals(active) ? conversations.get(0).id : active;
        persistConversations(conversations, nextActive);
    }

    void setActiveConversation(String id) {
        preferences.edit().putString(CURRENT_CONVERSATION, id).apply();
    }

    private List<ChatMessage> loadLegacyHistory() {
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

    private void persistConversations(List<Conversation> source, String activeId) {
        List<Conversation> ordered = new ArrayList<>(source);
        ordered.sort(Comparator.comparingLong(
                (Conversation conversation) -> conversation.updatedAt
        ).reversed());
        JSONArray array = new JSONArray();
        for (Conversation conversation : ordered) {
            try {
                array.put(conversation.toJson(MAX_MESSAGES));
            } catch (JSONException ignored) {
                // JSONObject with primitive fields should not fail.
            }
        }
        preferences.edit()
                .putString(CONVERSATIONS, array.toString())
                .putString(CURRENT_CONVERSATION, activeId)
                .remove(HISTORY)
                .apply();
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

}
