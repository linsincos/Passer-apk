package com.passer.aira;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PasserLinkConfigTest {
    @Test
    public void normalizesConfigWithoutChangingDeviceId() {
        PasserLinkConfig config =
                new PasserLinkConfig(" 192.168.10.8 ", 50720, "device_123", " 64295173 ");

        assertEquals("192.168.10.8", config.host);
        assertEquals(50720, config.port);
        assertEquals("device_123", config.deviceId);
        assertEquals("64295173", config.connectionCode);
        assertTrue(config.isConfigured());
    }

    @Test
    public void keepsDiscoveredComputerIdentityWithThePairing() {
        PasserLinkConfig config = new PasserLinkConfig(
                "192.168.1.30",
                50720,
                "device_123",
                "12345678"
        ).withComputerIdentity(
                "00112233445566778899aabbccddeeff",
                "OFFICE-PC"
        );
        assertEquals("00112233445566778899aabbccddeeff", config.computerId);
        assertEquals("OFFICE-PC", config.computerName);
        config.validate();
    }

    @Test
    public void acceptsOnlyLocalAddressRanges() {
        assertTrue(PasserLinkConfig.isAllowedHost("127.0.0.1"));
        assertTrue(PasserLinkConfig.isAllowedHost("10.20.30.40"));
        assertTrue(PasserLinkConfig.isAllowedHost("172.16.5.4"));
        assertTrue(PasserLinkConfig.isAllowedHost("192.168.1.9"));
        assertTrue(PasserLinkConfig.isAllowedHost("169.254.10.3"));
        assertTrue(PasserLinkConfig.isAllowedHost("::1"));
        assertTrue(PasserLinkConfig.isAllowedHost("fd12:3456::10"));

        assertFalse(PasserLinkConfig.isAllowedHost("8.8.8.8"));
        assertFalse(PasserLinkConfig.isAllowedHost("example.com"));
        assertFalse(PasserLinkConfig.isAllowedHost("100.64.0.1"));
        assertFalse(PasserLinkConfig.isAllowedHost("0.0.0.0"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidPort() {
        new PasserLinkConfig("192.168.1.2", 70000, "device_123", "12345678").validate();
    }
}
