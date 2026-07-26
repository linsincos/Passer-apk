package com.passer.aira;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

final class PasserLinkClient {
    static final String PROTOCOL = "aira-passer-v1";
    static final int PBKDF2_ROUNDS = 120_000;

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 12_000;
    private static final int MAX_LINE_BYTES = 64 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasserLinkConfig config;

    PasserLinkClient(PasserLinkConfig config) {
        this.config = config;
    }

    JSONObject request(String action, JSONObject params) throws Exception {
        config.validate();
        if (config.connectionCode.isEmpty()) {
            throw new IllegalStateException("请先保存 Passer 连接码。");
        }
        String cleanAction = action == null ? "" : action.trim();
        if (cleanAction.isEmpty()) {
            throw new IllegalArgumentException("桌面动作不能为空。");
        }
        JSONObject safeParams = params == null ? new JSONObject() : params;
        String paramsJson = safeParams.toString();
        String clientNonce = randomHex(16);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(config.host, config.port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            try (InputStream input = socket.getInputStream();
                 BufferedWriter output = new BufferedWriter(new OutputStreamWriter(
                         socket.getOutputStream(), StandardCharsets.UTF_8))) {
                write(output, new JSONObject()
                        .put("protocol", PROTOCOL)
                        .put("auth", "hello")
                        .put("client_nonce", clientNonce)
                        .put("device_id", config.deviceId)
                        .put("device_name", android.os.Build.MANUFACTURER + " "
                                + android.os.Build.MODEL));

                JSONObject challenge = readJson(input);
                requireProtocol(challenge);
                if (!"challenge".equals(challenge.optString("auth"))) {
                    throw new SecurityException("Passer 没有返回有效的认证挑战。");
                }
                String serverNonce = challenge.optString("server_nonce", "");
                if (!isHex(serverNonce, 32)) {
                    throw new SecurityException("Passer 服务端随机数无效。");
                }
                int rounds = challenge.optInt("rounds", -1);
                if (rounds != PBKDF2_ROUNDS) {
                    throw new SecurityException("Passer 使用了不受支持的认证迭代次数。");
                }

                String proof = clientProof(
                        config.connectionCode,
                        clientNonce,
                        serverNonce,
                        config.deviceId,
                        cleanAction,
                        paramsJson,
                        rounds
                );
                write(output, new JSONObject()
                        .put("auth", "response")
                        .put("client_nonce", clientNonce)
                        .put("action", cleanAction)
                        .put("params_json", paramsJson)
                        .put("proof", proof));

                JSONObject envelope = readJson(input);
                requireProtocol(envelope);
                if (!envelope.optBoolean("ok", false)) {
                    throw new IllegalStateException(errorMessage(envelope));
                }
                if (!"ok".equals(envelope.optString("auth"))) {
                    throw new SecurityException("Passer 认证响应无效。");
                }
                String resultJson = envelope.optString("result_json", "");
                String expected = serverProof(
                        config.connectionCode,
                        clientNonce,
                        serverNonce,
                        config.deviceId,
                        cleanAction,
                        resultJson,
                        rounds
                );
                if (!constantTimeHexEquals(expected, envelope.optString("server_proof", ""))) {
                    throw new SecurityException("Passer 响应证明校验失败，结果已丢弃。");
                }
                JSONObject result = new JSONObject(resultJson);
                if (!result.optBoolean("ok", true)) {
                    throw new IllegalStateException(errorMessage(result));
                }
                return result;
            }
        }
    }

    static String clientProof(
            String code,
            String clientNonce,
            String serverNonce,
            String deviceId,
            String action,
            String paramsJson,
            int rounds
    ) throws Exception {
        byte[] key = deriveKey(code, serverNonce, rounds);
        String content = PROTOCOL + "|client|" + clientNonce + "|" + serverNonce + "|"
                + deviceId + "|" + action + "|" + sha256Hex(paramsJson);
        return hmacHex(key, content);
    }

    static String serverProof(
            String code,
            String clientNonce,
            String serverNonce,
            String deviceId,
            String action,
            String resultJson,
            int rounds
    ) throws Exception {
        byte[] key = deriveKey(code, serverNonce, rounds);
        String content = PROTOCOL + "|server|" + clientNonce + "|" + serverNonce + "|"
                + deviceId + "|" + action + "|" + sha256Hex(resultJson);
        return hmacHex(key, content);
    }

    static String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] deriveKey(String code, String serverNonce, int rounds)
            throws Exception {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("连接码不能为空。");
        }
        if (!isHex(serverNonce, 32)) {
            throw new IllegalArgumentException("服务端随机数必须是 32 位十六进制。");
        }
        PBEKeySpec spec = new PBEKeySpec(
                code.toCharArray(),
                fromHex(serverNonce),
                rounds,
                256
        );
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static String hmacHex(byte[] key, String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return toHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    private static JSONObject readJson(InputStream input) throws Exception {
        String line = readLimitedLine(input);
        if (line == null) {
            throw new EOFException("Passer 提前关闭了连接。");
        }
        return new JSONObject(line);
    }

    private static String readLimitedLine(InputStream input) throws Exception {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                result.write(value);
                if (result.size() > MAX_LINE_BYTES) {
                    throw new IllegalStateException("Passer 响应过大。");
                }
            }
        }
        return value == -1 && result.size() == 0
                ? null
                : result.toString(StandardCharsets.UTF_8.name());
    }

    private static void write(BufferedWriter output, JSONObject value) throws Exception {
        output.write(value.toString());
        output.write('\n');
        output.flush();
    }

    private static void requireProtocol(JSONObject value) {
        String protocol = value.optString("protocol", PROTOCOL);
        if (!PROTOCOL.equals(protocol)) {
            throw new SecurityException("Passer 协议版本不匹配。");
        }
    }

    private static String errorMessage(JSONObject value) {
        String message = value.optString("error", "").trim();
        if (message.isEmpty()) {
            message = value.optString("message", "").trim();
        }
        return message.isEmpty() ? "Passer 拒绝了请求。" : message;
    }

    private static String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    private static boolean isHex(String value, int length) {
        return value != null
                && value.length() == length
                && value.matches("[0-9a-fA-F]+");
    }

    private static byte[] fromHex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static String toHex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
        }
        return result.toString();
    }

    private static boolean constantTimeHexEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                (actual == null ? "" : actual.toLowerCase(java.util.Locale.ROOT))
                        .getBytes(StandardCharsets.US_ASCII)
        );
    }
}
