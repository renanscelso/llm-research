package br.com.renan.llmresearch.config;

import br.com.renan.llmresearch.provider.AnthropicClient;
import br.com.renan.llmresearch.provider.DeepSeekClient;
import br.com.renan.llmresearch.provider.GeminiClient;
import br.com.renan.llmresearch.provider.GrokClient;
import br.com.renan.llmresearch.provider.LlamaClient;
import br.com.renan.llmresearch.provider.LlmClient;
import br.com.renan.llmresearch.provider.MisconfiguredLlmClient;
import br.com.renan.llmresearch.provider.MistralClient;
import br.com.renan.llmresearch.provider.OpenAiClient;
import br.com.renan.llmresearch.provider.PerplexityClient;
import br.com.renan.llmresearch.provider.QwenClient;
import br.com.renan.llmresearch.provider.RetryingLlmClient;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Registra os adaptadores dos provedores habilitados.
 *
 * @author Renan Celso
 */
@Configuration(proxyBeanMethods = false)
public class LlmProvidersConfiguration {

    private static final double TEMPERATURE = 0.7d;

    @Bean
    public List<LlmClient> llmClients(ResearchProperties properties) {
        List<LlmClient> clients = new ArrayList<>();
        for (String providerId : ResearchProperties.PROVIDER_ORDER) {
            ResearchProperties.ProviderProperties provider = properties.providers().get(providerId);
            if (provider == null || !provider.enabled()) {
                continue;
            }
            LlmClient client = createClient(providerId, provider, properties.requestTimeoutSeconds());
            if (!(client instanceof MisconfiguredLlmClient)) {
                client = new RetryingLlmClient(
                        client,
                        properties.maxAttempts(),
                        properties.retryDelayMs()
                );
            }
            clients.add(client);
        }
        return List.copyOf(clients);
    }

    private LlmClient createClient(
            String providerId,
            ResearchProperties.ProviderProperties provider,
            int timeoutSeconds
    ) {
        String missing = missingConfiguration(providerId, provider);
        if (missing != null) {
            return new MisconfiguredLlmClient(providerId, provider.model(), missing);
        }

        Duration timeout = Duration.ofSeconds(timeoutSeconds);

        return switch (providerId) {
            case "openai" -> new OpenAiClient(provider.model(), openAiChatModel(provider, timeout, null));
            case "grok" -> new GrokClient(provider.model(), openAiChatModel(provider, timeout, null));
            case "deepseek" -> new DeepSeekClient(provider.model(), openAiChatModel(provider, timeout, null));
            case "perplexity" -> new PerplexityClient(
                    provider.model(),
                    provider.apiKey(),
                    provider.baseUrl(),
                    timeout
            );
            case "qwen" -> new QwenClient(provider.model(), openAiChatModel(provider, timeout, null));
            case "llama" -> new LlamaClient(provider.model(), openAiChatModel(provider, timeout, 2048));
            case "anthropic" -> anthropic(provider, timeout);
            case "gemini" -> gemini(provider, timeout);
            case "mistral" -> mistral(provider, timeout);
            default -> new MisconfiguredLlmClient(
                    providerId,
                    provider.model(),
                    "Provedor desconhecido: " + providerId
            );
        };
    }

    private String missingConfiguration(
            String providerId,
            ResearchProperties.ProviderProperties provider
    ) {
        if (!provider.hasApiKey()) {
            return "Provedor " + providerId + " habilitado sem API key. Configure a variável de ambiente correspondente.";
        }
        if (!provider.hasModel()) {
            return "Provedor " + providerId + " habilitado sem modelo. Configure a variável de modelo correspondente.";
        }
        if (requiresBaseUrl(providerId) && !provider.hasBaseUrl()) {
            return "Provedor " + providerId + " habilitado sem base-url. Configure a URL do endpoint compatível.";
        }
        return null;
    }

    private boolean requiresBaseUrl(String providerId) {
        return switch (providerId) {
            case "openai", "anthropic", "grok", "mistral", "deepseek", "perplexity", "qwen", "llama" -> true;
            case "gemini" -> false;
            default -> true;
        };
    }

    private OpenAiChatModel openAiChatModel(
            ResearchProperties.ProviderProperties provider,
            Duration timeout,
            Integer maxCompletionTokens
    ) {
        var optionsBuilder = OpenAiChatOptions.builder()
                .apiKey(provider.apiKey())
                .baseUrl(provider.baseUrl())
                .model(provider.model())
                .temperature(TEMPERATURE)
                .maxRetries(0)
                .timeout(timeout);

        if (maxCompletionTokens != null && maxCompletionTokens > 0) {
            optionsBuilder.maxCompletionTokens(maxCompletionTokens);
        }

        return OpenAiChatModel.builder()
                .options(optionsBuilder.build())
                .build();
    }

    private LlmClient anthropic(ResearchProperties.ProviderProperties provider, Duration timeout) {
        // claude-sonnet-5-5 rejeita temperature (deprecated / invalid_request_error)
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .apiKey(provider.apiKey())
                .baseUrl(provider.baseUrl())
                .model(provider.model())
                .maxTokens(4096)
                .maxRetries(0)
                .timeout(timeout)
                .build();

        AnthropicChatModel chatModel = AnthropicChatModel.builder()
                .options(options)
                .build();

        return new AnthropicClient(provider.model(), chatModel);
    }

    private LlmClient gemini(ResearchProperties.ProviderProperties provider, Duration timeout) {
        HttpOptions.Builder httpOptions = HttpOptions.builder()
                .timeout((int) timeout.toMillis());

        if (StringUtils.hasText(provider.baseUrl())) {
            httpOptions.baseUrl(provider.baseUrl());
        }

        Client genAiClient = Client.builder()
                .apiKey(provider.apiKey())
                .httpOptions(httpOptions.build())
                .build();

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(provider.model())
                .temperature(TEMPERATURE)
                .build();

        GoogleGenAiChatModel chatModel = GoogleGenAiChatModel.builder()
                .genAiClient(genAiClient)
                .options(options)
                .retryTemplate(RetryTemplates.noRetry())
                .build();

        return new GeminiClient(provider.model(), chatModel);
    }

    private LlmClient mistral(ResearchProperties.ProviderProperties provider, Duration timeout) {
        MistralAiApi api = MistralAiApi.builder()
                .apiKey(provider.apiKey())
                .baseUrl(provider.baseUrl())
                .build();

        MistralAiChatOptions options = MistralAiChatOptions.builder()
                .model(provider.model())
                .temperature(TEMPERATURE)
                .build();

        MistralAiChatModel chatModel = MistralAiChatModel.builder()
                .mistralAiApi(api)
                .options(options)
                .retryTemplate(RetryTemplates.noRetry())
                .build();

        return new MistralClient(provider.model(), chatModel);
    }
}
