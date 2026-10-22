package br.com.renan.llmresearch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "research.runner.enabled=false",
        "research.providers.openai.enabled=false",
        "research.providers.anthropic.enabled=false",
        "research.providers.gemini.enabled=false",
        "research.providers.grok.enabled=false",
        "research.providers.mistral.enabled=false",
        "research.providers.deepseek.enabled=false",
        "research.providers.perplexity.enabled=false",
        "research.providers.qwen.enabled=false",
        "research.providers.llama.enabled=false"
})
class LlmResearchApplicationTests {

    @Test
    void contextLoads() {
    }
}
