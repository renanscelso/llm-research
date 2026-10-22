package br.com.renan.llmresearch.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderErrorMapperTest {

    @Test
    void mapsCommonHttpFailures() {
        assertEquals(
                "Credenciais inválidas ou não autorizadas (HTTP 401).",
                ProviderErrorMapper.map(new RuntimeException("HTTP 401 Unauthorized"))
        );
        assertEquals(
                "Limite de requisições atingido (HTTP 429).",
                ProviderErrorMapper.map(new RuntimeException("429 rate limit exceeded"))
        );
        assertEquals(
                "Timeout na chamada ao provedor.",
                ProviderErrorMapper.map(new RuntimeException("Read timed out"))
        );
        assertEquals(
                "Falha de rede/DNS ao contactar o provedor (host inacessível).",
                ProviderErrorMapper.map(new RuntimeException("Request failed"))
        );
    }

    @Test
    void redactsSecretsFromMessages() {
        String mapped = ProviderErrorMapper.map(
                new RuntimeException("api_key=sk-abcdefghijklmnopqrstuvwxyz123456 authorization=Bearer secret")
        );
        assertTrue(mapped.contains("REDACTED"));
        assertFalse(mapped.contains("sk-abcdefghijklmnopqrstuvwxyz123456"));
        assertFalse(mapped.toLowerCase().contains("bearer secret"));
    }
}
