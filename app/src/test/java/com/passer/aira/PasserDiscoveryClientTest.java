package com.passer.aira;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PasserDiscoveryClientTest {
    @Test
    public void acceptsOnlyRandomLookingComputerIds() {
        assertTrue(PasserDiscoveryClient.isValidComputerId(
                "00112233445566778899aabbccddeeff"));
        assertFalse(PasserDiscoveryClient.isValidComputerId(""));
        assertFalse(PasserDiscoveryClient.isValidComputerId("office-pc"));
        assertFalse(PasserDiscoveryClient.isValidComputerId(
                "00112233445566778899aabbccddeefg"));
    }
}
