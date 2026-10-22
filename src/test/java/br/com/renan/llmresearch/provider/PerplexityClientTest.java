package br.com.renan.llmresearch.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerplexityClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void normalizesResponsesUrl() {
        assertEquals(
                "https://api.perplexity.ai/v1/responses",
                PerplexityClient.normalizeResponsesUrl("https://api.perplexity.ai/v1")
        );
        assertEquals(
                "https://api.perplexity.ai/v1/responses",
                PerplexityClient.normalizeResponsesUrl("https://api.perplexity.ai")
        );
        assertEquals(
                "https://api.perplexity.ai/v1/responses",
                PerplexityClient.normalizeResponsesUrl("https://api.perplexity.ai/v1/responses")
        );
    }

    @Test
    void extractsTextFromOutputArray() throws Exception {
        String payload = """
                {
                  "model": "perplexity/sonar",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        { "type": "output_text", "text": "Saúde pública é importante." }
                      ]
                    }
                  ],
                  "usage": { "input_tokens": 10, "output_tokens": 5 }
                }
                """;
        String text = PerplexityClient.extractText(mapper.readTree(payload));
        assertEquals("Saúde pública é importante.", text);
    }

    @Test
    void extractsOutputTextFieldWhenPresent() throws Exception {
        String payload = """
                { "output_text": "ok" }
                """;
        assertEquals("ok", PerplexityClient.extractText(mapper.readTree(payload)));
        assertTrue(PerplexityClient.extractText(mapper.readTree("{}")) == null
                || PerplexityClient.extractText(mapper.readTree("{}")).isBlank());
    }
}
