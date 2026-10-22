package br.com.renan.llmresearch.provider;

import br.com.renan.llmresearch.model.Question;
import br.com.renan.llmresearch.model.ResearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Adaptador Perplexity via Agent API ({@code POST /v1/responses}).
 * A API antiga de chat completions (Sonar) foi descontinuada.
 *
 * @author Renan Celso
 */
public class PerplexityClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String configuredModel;
    private final String apiKey;
    private final String responsesUrl;
    private final Duration timeout;
    private final HttpClient httpClient;

    public PerplexityClient(String configuredModel, String apiKey, String baseUrl, Duration timeout) {
        this.configuredModel = configuredModel;
        this.apiKey = apiKey;
        this.timeout = timeout == null ? Duration.ofSeconds(120) : timeout;
        this.responsesUrl = normalizeResponsesUrl(baseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    @Override
    public String providerId() {
        return "perplexity";
    }

    @Override
    public String configuredModel() {
        return configuredModel;
    }

    @Override
    public ResearchResult ask(Question question, String executionId, int repetition) {
        long started = System.nanoTime();
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", configuredModel);
            body.put("input", question.text());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(responsesUrl))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latencyMs = elapsedMs(started);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return ResearchResult.error(
                        executionId,
                        question,
                        providerId(),
                        configuredModel,
                        repetition,
                        mapHttpError(response.statusCode(), response.body()),
                        latencyMs
                );
            }

            JsonNode root = MAPPER.readTree(response.body());
            String text = extractText(root);
            if (text == null || text.isBlank()) {
                return ResearchResult.error(
                        executionId,
                        question,
                        providerId(),
                        configuredModel,
                        repetition,
                        "Resposta vazia do modelo.",
                        latencyMs
                );
            }

            String effectiveModel = configuredModel;
            if (root.path("model").isTextual() && !root.path("model").asText().isBlank()) {
                effectiveModel = root.path("model").asText();
            }

            Integer inputTokens = null;
            Integer outputTokens = null;
            JsonNode usage = root.path("usage");
            if (usage.isObject()) {
                if (usage.path("input_tokens").isNumber()) {
                    inputTokens = usage.path("input_tokens").asInt();
                }
                if (usage.path("output_tokens").isNumber()) {
                    outputTokens = usage.path("output_tokens").asInt();
                }
            }

            return ResearchResult.success(
                    executionId,
                    question,
                    providerId(),
                    effectiveModel,
                    repetition,
                    text,
                    latencyMs,
                    inputTokens,
                    outputTokens
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ResearchResult.error(
                    executionId,
                    question,
                    providerId(),
                    configuredModel,
                    repetition,
                    "Chamada interrompida.",
                    elapsedMs(started)
            );
        } catch (Exception ex) {
            return ResearchResult.error(
                    executionId,
                    question,
                    providerId(),
                    configuredModel,
                    repetition,
                    ProviderErrorMapper.map(ex),
                    elapsedMs(started)
            );
        }
    }

    static String normalizeResponsesUrl(String baseUrl) {
        String base = (baseUrl == null || baseUrl.isBlank())
                ? "https://api.perplexity.ai/v1"
                : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/responses")) {
            return base;
        }
        if (base.endsWith("/v1")) {
            return base + "/responses";
        }
        if (base.equals("https://api.perplexity.ai")) {
            return base + "/v1/responses";
        }
        return base + "/responses";
    }

    static String extractText(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.path("output_text").isTextual() && !root.path("output_text").asText().isBlank()) {
            return root.path("output_text").asText();
        }
        JsonNode output = root.path("output");
        if (output.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode part : content) {
                        if ("output_text".equals(part.path("type").asText())
                                && part.path("text").isTextual()) {
                            if (!text.isEmpty()) {
                                text.append('\n');
                            }
                            text.append(part.path("text").asText());
                        }
                    }
                }
            }
            if (!text.isEmpty()) {
                return text.toString();
            }
        }
        return null;
    }

    private static String mapHttpError(int status, String body) {
        String snippet = body == null ? "" : body;
        if (snippet.length() > 300) {
            snippet = snippet.substring(0, 300);
        }
        RuntimeException fake = new RuntimeException("HTTP " + status + " " + snippet);
        return ProviderErrorMapper.map(fake);
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
