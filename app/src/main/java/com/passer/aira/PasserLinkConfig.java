package com.passer.aira;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Locale;
import java.util.UUID;

final class PasserLinkConfig {
    static final String DEFAULT_HOST = "127.0.0.1";
    static final int DEFAULT_PORT = 50720;

    private static final String PREFS = "aira_passer_link";
    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String DEVICE_ID = "device_id";
    private static final String COMPUTER_ID = "computer_id";
    private static final String COMPUTER_NAME = "computer_name";

    final String host;
    final int port;
    final String deviceId;
    final String connectionCode;
    final String computerId;
    final String computerName;

    PasserLinkConfig(String host, int port, String deviceId, String connectionCode) {
        this(host, port, deviceId, connectionCode, "", "");
    }

    PasserLinkConfig(
            String host,
            int port,
            String deviceId,
            String connectionCode,
            String computerId,
            String computerName
    ) {
        this.host = normalizeHost(host);
        this.port = port;
        this.deviceId = normalizeDeviceId(deviceId);
        this.connectionCode = connectionCode == null ? "" : connectionCode.trim();
        this.computerId = computerId == null ? "" : computerId.trim();
        this.computerName = computerName == null ? "" : computerName.trim();
    }

    static PasserLinkConfig load(Context context, SecureStore secureStore) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String deviceId = preferences.getString(DEVICE_ID, "");
        if (deviceId == null || deviceId.trim().isEmpty()) {
            deviceId = UUID.randomUUID().toString();
            preferences.edit().putString(DEVICE_ID, deviceId).apply();
        }
        return new PasserLinkConfig(
                preferences.getString(HOST, DEFAULT_HOST),
                preferences.getInt(PORT, DEFAULT_PORT),
                deviceId,
                secureStore.loadPasserConnectionCode(),
                preferences.getString(COMPUTER_ID, ""),
                preferences.getString(COMPUTER_NAME, "")
        );
    }

    void save(Context context, SecureStore secureStore, boolean replaceCode) throws Exception {
        validate();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(HOST, host)
                .putInt(PORT, port)
                .putString(DEVICE_ID, deviceId)
                .putString(COMPUTER_ID, computerId)
                .putString(COMPUTER_NAME, computerName)
                .apply();
        if (replaceCode) {
            secureStore.savePasserConnectionCode(connectionCode);
        }
    }

    static void disconnect(Context context, SecureStore secureStore) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String deviceId = preferences.getString(DEVICE_ID, "");
        preferences.edit().clear().putString(DEVICE_ID, deviceId).apply();
        secureStore.clearPasserConnectionCode();
    }

    boolean isConfigured() {
        return connectionCode.matches("[0-9]{8}")
                && port >= 1
                && port <= 65535
                && isAllowedHost(host)
                && isValidDeviceId(deviceId);
    }

    PasserLinkConfig withComputerIdentity(String id, String name) {
        return new PasserLinkConfig(
                host,
                port,
                deviceId,
                connectionCode,
                id,
                name
        );
    }

    void validate() {
        if (!isAllowedHost(host)) {
            throw new IllegalArgumentException(
                    "主机必须是回环、局域网或链路本地 IP 地址，不能使用公网地址或域名。");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("端口必须在 1–65535 之间。");
        }
        if (!isValidDeviceId(deviceId)) {
            throw new IllegalArgumentException("设备 ID 无效。");
        }
        if (!connectionCode.isEmpty() && !connectionCode.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("连接码必须是 8 位数字。");
        }
        if (!computerId.isEmpty() && !PasserDiscoveryClient.isValidComputerId(computerId)) {
            throw new IllegalArgumentException("电脑 ID 无效。");
        }
        if (computerName.length() > 80) {
            throw new IllegalArgumentException("电脑名称过长。");
        }
    }

    static boolean isAllowedHost(String value) {
        String host = normalizeHost(value);
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        if (host.isEmpty()) {
            return false;
        }
        if (host.indexOf(':') < 0) {
            return isAllowedIpv4(host);
        }
        String addressOnly = host;
        int zone = addressOnly.indexOf('%');
        if (zone >= 0) {
            addressOnly = addressOnly.substring(0, zone);
        }
        if (!addressOnly.matches("[0-9a-fA-F:]+")) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(addressOnly);
            if (!(address instanceof Inet6Address)) {
                return false;
            }
            byte[] raw = address.getAddress();
            boolean uniqueLocal = raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
            return address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || uniqueLocal;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isAllowedIpv4(String host) {
        String[] pieces = host.split("\\.", -1);
        if (pieces.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        for (int i = 0; i < pieces.length; i++) {
            if (pieces[i].isEmpty() || !pieces[i].matches("[0-9]{1,3}")) {
                return false;
            }
            try {
                octets[i] = Integer.parseInt(pieces[i]);
            } catch (NumberFormatException ignored) {
                return false;
            }
            if (octets[i] > 255) {
                return false;
            }
        }
        return octets[0] == 10
                || octets[0] == 127
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168)
                || (octets[0] == 169 && octets[1] == 254);
    }

    private static boolean isValidDeviceId(String value) {
        return value != null
                && value.length() >= 8
                && value.length() <= 80
                && value.matches("[A-Za-z0-9._-]+");
    }

    private static String normalizeHost(String value) {
        String host = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
            host = host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static String normalizeDeviceId(String value) {
        return value == null ? "" : value.trim();
    }
}
