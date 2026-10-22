package br.com.renan.llmresearch.config;

import br.com.renan.llmresearch.provider.LlmClient;
import br.com.renan.llmresearch.provider.MisconfiguredLlmClient;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmProvidersConfigurationTest {

    @Test
    void selectsOnlyEnabledProviders() {
        Map<String, ResearchProperties.ProviderProperties> providers = new LinkedHashMap<>();
        providers.put("openai", new ResearchProperties.ProviderProperties(
                true, "https://api.openai.com/v1", "sk-test", "gpt-4.1-mini"));
        providers.put("anthropic", new ResearchProperties.ProviderProperties(
                false, "https://api.anthropic.com", "key", "claude"));
        providers.put("llama", new ResearchProperties.ProviderProperties(
                true, "", "key", "llama-3"));

        ResearchProperties properties = new ResearchProperties(
                new ResearchProperties.RunnerProperties(false),
                1,
                "./config/questions.csv",
                "./output",
                120,
                3,
                0L,
                0L,
                false,
                providers
        );

        List<LlmClient> clients = new LlmProvidersConfiguration().llmClients(properties);

        assertEquals(2, clients.size());
        assertEquals("openai", clients.get(0).providerId());
        assertEquals("llama", clients.get(1).providerId());
        assertInstanceOf(MisconfiguredLlmClient.class, clients.get(1));
        assertTrue(clients.get(1).ask(
                new br.com.renan.llmresearch.model.Question(1, "teste"),
                "exec",
                1
        ).error().contains("base-url"));
    }

    @Test
    void disabledProvidersDoNotRequireCredentials() {
        Map<String, ResearchProperties.ProviderProperties> providers = Map.of(
                "openai", new ResearchProperties.ProviderProperties(false, null, null, null)
        );

        ResearchProperties properties = new ResearchProperties(
                new ResearchProperties.RunnerProperties(false),
                1,
                "./config/questions.csv",
                "./output",
                120,
                3,
                0L,
                0L,
                false,
                providers
        );

        List<LlmClient> clients = new LlmProvidersConfiguration().llmClients(properties);
        assertTrue(clients.isEmpty());
    }
}
