package com.passer.aira;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "aira_api_key_v1";
    private static final String PREFS = "aira_secure";
    private static final String VALUE = "api_key";
    private static final String IV = "api_key_iv";
    private static final String PASSER_CODE = "passer_connection_code";
    private static final String PASSER_CODE_IV = "passer_connection_code_iv";

    private final SharedPreferences preferences;

    SecureStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized void saveApiKey(String value) throws Exception {
        saveSecret(VALUE, IV, value);
    }

    synchronized String loadApiKey() {
        return loadSecret(VALUE, IV);
    }

    synchronized void clearApiKey() {
        clearSecret(VALUE, IV);
    }

    synchronized void savePasserConnectionCode(String value) throws Exception {
        saveSecret(PASSER_CODE, PASSER_CODE_IV, value);
    }

    synchronized String loadPasserConnectionCode() {
        return loadSecret(PASSER_CODE, PASSER_CODE_IV);
    }

    synchronized void clearPasserConnectionCode() {
        clearSecret(PASSER_CODE, PASSER_CODE_IV);
    }

    private void saveSecret(String valueKey, String ivKey, String value) throws Exception {
        if (value == null || value.isEmpty()) {
            clearSecret(valueKey, ivKey);
            return;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        preferences.edit()
                .putString(valueKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(ivKey, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .apply();
    }

    private String loadSecret(String valueKey, String ivKey) {
        String encoded = preferences.getString(valueKey, "");
        String encodedIv = preferences.getString(ivKey, "");
        if (encoded == null || encodedIv == null || encoded.isEmpty() || encodedIv.isEmpty()) {
            return "";
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP))
            );
            byte[] plain = cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private void clearSecret(String valueKey, String ivKey) {
        preferences.edit().remove(valueKey).remove(ivKey).apply();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        KeyStore.Entry entry = store.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
