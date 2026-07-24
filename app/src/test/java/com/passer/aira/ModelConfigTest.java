package com.passer.aira;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ModelConfigTest {
    @Test
    public void defaultsToDeepSeekForUnknownProvider() {
        ModelConfig config = new ModelConfig("unknown", "", " key ");

        assertEquals(ModelConfig.DEEPSEEK, config.provider);
        assertEquals("deepseek-chat", config.model);
        assertEquals("key", config.apiKey);
    }

    @Test
    public void selectsProviderSpecificDefaults() {
        assertEquals("gpt-4.1-mini", ModelConfig.defaultModel(ModelConfig.OPENAI));
        assertEquals("claude-sonnet-4-6", ModelConfig.defaultModel(ModelConfig.ANTHROPIC));
    }

    @Test
    public void preservesExplicitModel() {
        ModelConfig config = new ModelConfig(ModelConfig.OPENAI, "gpt-custom", "");

        assertEquals("gpt-custom", config.model);
        assertEquals("OpenAI", ModelConfig.displayName(config.provider));
    }
}
