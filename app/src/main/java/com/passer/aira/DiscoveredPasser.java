package com.passer.aira;

final class DiscoveredPasser {
    final String computerId;
    final String computerName;
    final String host;
    final int port;

    DiscoveredPasser(String computerId, String computerName, String host, int port) {
        this.computerId = computerId == null ? "" : computerId.trim();
        this.computerName = computerName == null || computerName.trim().isEmpty()
                ? "Passer"
                : computerName.trim();
        this.host = host == null ? "" : host.trim();
        this.port = port;
    }

    String key() {
        return computerId.isEmpty() ? host + ":" + port : computerId;
    }

    String displayName() {
        return computerName + "\n" + host + ":" + port;
    }
}
