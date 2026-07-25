package com.passer.aira;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public final class ConversationTest {
    @Test
    public void derivesCompactTitleFromFirstUserMessage() {
        String title = Conversation.titleFromMessages(Arrays.asList(
                new ChatMessage("assistant", "欢迎"),
                new ChatMessage("user", "  帮我\n规划   明天的安排  ")
        ));

        assertEquals("帮我 规划 明天的安排", title);
    }

    @Test
    public void emptyTitleUsesDefault() {
        assertEquals(Conversation.DEFAULT_TITLE, Conversation.normalizeTitle("  "));
    }
}
