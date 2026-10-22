package br.com.renan.llmresearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configurações centralizadas do experimento.
 *
 * @author Renan Celso
 */
@ConfigurationProperties(prefix = "research")
public record ResearchProperties(
        RunnerProperties runner,
        int repetitions,
        String questionsFile,
        String outputDirectory,
        int requestTimeoutSeconds,
        int maxAttempts,
        long retryDelayMs,
        long interRequestDelayMs,
        boolean parallelProviders,
        Map<String, ProviderProperties> providers
) {

    public static final List<String> PROVIDER_ORDER = List.of(
            "openai",
            "anthropic",
            "gemini",
            "grok",
            "mistral",
            "deepseek",
            "perplexity",
            "qwen",
            "llama"
    );

    public ResearchProperties {
        runner = runner == null ? new RunnerProperties(true) : runner;
        requestTimeoutSeconds = requestTimeoutSeconds <= 0 ? 120 : requestTimeoutSeconds;
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
        retryDelayMs = retryDelayMs < 0 ? 1000L : retryDelayMs;
        interRequestDelayMs = interRequestDelayMs < 0 ? 0L : interRequestDelayMs;
        providers = providers == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(providers));
    }

    public record RunnerProperties(boolean enabled) {
    }

    public record ProviderProperties(
            boolean enabled,
            String baseUrl,
            String apiKey,
            String model
    ) {
        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        public boolean hasModel() {
            return model != null && !model.isBlank();
        }

        public boolean hasBaseUrl() {
            return baseUrl != null && !baseUrl.isBlank();
        }

        @Override
        public String toString() {
            return "ProviderProperties[enabled=" + enabled
                    + ", baseUrl=" + baseUrl
                    + ", model=" + model
                    + ", apiKey=REDACTED]";
        }
    }
}
