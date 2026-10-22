package br.com.renan.llmresearch.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "research.runner.enabled=false",
        "research.repetitions=2",
        "research.providers.openai.enabled=false",
        "research.providers.anthropic.enabled=true",
        "research.providers.anthropic.api-key=test-key",
        "research.providers.anthropic.model=claude-test",
        "research.providers.gemini.enabled=false",
        "research.providers.grok.enabled=false",
        "research.providers.mistral.enabled=false",
        "research.providers.deepseek.enabled=false",
        "research.providers.perplexity.enabled=false",
        "research.providers.qwen.enabled=false",
        "research.providers.llama.enabled=false"
})
class ResearchPropertiesTest {

    @Autowired
    private ResearchProperties properties;

    @Test
    void bindsProviderConfiguration() {
        assertEquals(2, properties.repetitions());
        assertFalse(properties.providers().get("openai").enabled());
        assertTrue(properties.providers().get("anthropic").enabled());
        assertEquals("claude-test", properties.providers().get("anthropic").model());
        assertTrue(properties.providers().get("anthropic").toString().contains("REDACTED"));
        assertFalse(properties.providers().get("anthropic").toString().contains("test-key"));
    }

    @Test
    void providerOrderContainsEightEcosystems() {
        assertEquals(9, ResearchProperties.PROVIDER_ORDER.size());
        assertTrue(ResearchProperties.PROVIDER_ORDER.contains("openai"));
        assertTrue(ResearchProperties.PROVIDER_ORDER.contains("deepseek"));
        assertTrue(ResearchProperties.PROVIDER_ORDER.contains("llama"));
    }
}
