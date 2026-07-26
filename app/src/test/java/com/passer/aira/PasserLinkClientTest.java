package com.passer.aira;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PasserLinkClientTest {
    @Test
    public void clientProofMatchesProtocolVector() throws Exception {
        assertEquals(
                "a5da9dec167ecb3126d8a7bf7ca436aa0a41992d0861c43a7f8c9a33608d2e9c",
                PasserLinkClient.clientProof(
                        "24681012",
                        "00112233445566778899aabbccddeeff",
                        "ffeeddccbbaa99887766554433221100",
                        "device-test-1",
                        "search",
                        "{}",
                        120_000
                )
        );
    }

    @Test
    public void sha256UsesUtf8() throws Exception {
        assertEquals(
                "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
                PasserLinkClient.sha256Hex("{}")
        );
    }
}
