package com.passer.aira;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PasserDiscoveryClient {
    static final String PROTOCOL = "aira-passer-discovery-v1";
    static final int PORT = 50721;
    static final byte[] REQUEST =
            "AIRA_PASSER_DISCOVER_V1\n".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_RESPONSE_BYTES = 2048;

    List<DiscoveredPasser> discover(int timeoutMillis) throws Exception {
        int safeTimeout = Math.max(500, Math.min(8_000, timeoutMillis));
        long deadline = android.os.SystemClock.elapsedRealtime() + safeTimeout;
        Map<String, DiscoveredPasser> found = new LinkedHashMap<>();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(250);
            for (InetAddress address : broadcastAddresses()) {
                DatagramPacket request = new DatagramPacket(
                        REQUEST,
                        REQUEST.length,
                        address,
                        PORT
                );
                try {
                    socket.send(request);
                } catch (Exception ignored) {
                    // Continue with the other active interfaces.
                }
            }

            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                byte[] buffer = new byte[MAX_RESPONSE_BYTES];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(response);
                } catch (java.net.SocketTimeoutException ignored) {
                    continue;
                }
                String host = response.getAddress().getHostAddress();
                if (!PasserLinkConfig.isAllowedHost(host)) {
                    continue;
                }
                String raw = new String(
                        response.getData(),
                        response.getOffset(),
                        response.getLength(),
                        StandardCharsets.UTF_8
                ).trim();
                try {
                    JSONObject value = new JSONObject(raw);
                    if (!PROTOCOL.equals(value.optString("protocol"))
                            || !"aira-passer".equals(value.optString("service"))) {
                        continue;
                    }
                    int port = value.optInt("port", -1);
                    String computerId = value.optString("computer_id", "").trim();
                    if (port < 1 || port > 65535 || !isValidComputerId(computerId)) {
                        continue;
                    }
                    DiscoveredPasser computer = new DiscoveredPasser(
                            computerId,
                            value.optString("computer_name", "Passer"),
                            host,
                            port
                    );
                    found.put(computer.key(), computer);
                    if (found.size() >= 20) {
                        break;
                    }
                } catch (Exception ignored) {
                    // Ignore unrelated or malformed UDP replies on the LAN.
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    static boolean isValidComputerId(String value) {
        return value != null
                && value.length() == 32
                && value.matches("[0-9a-fA-F]{32}");
    }

    private List<InetAddress> broadcastAddresses() {
        List<InetAddress> values = new ArrayList<>();
        try {
            values.add(InetAddress.getByName("255.255.255.255"));
        } catch (Exception ignored) {
            // The per-interface addresses below are usually sufficient.
        }
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return values;
            }
            for (NetworkInterface network : Collections.list(interfaces)) {
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress item : network.getInterfaceAddresses()) {
                    InetAddress broadcast = item.getBroadcast();
                    if (broadcast instanceof Inet4Address && !values.contains(broadcast)) {
                        values.add(broadcast);
                    }
                }
            }
        } catch (Exception ignored) {
            // Keep the global broadcast fallback.
        }
        return values;
    }
}
