package br.com.renan.llmresearch.provider;

import br.com.renan.llmresearch.model.Question;
import br.com.renan.llmresearch.model.ResearchResult;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Adaptador genérico sobre {@link ChatModel} do Spring AI.
 *
 * @author Renan Celso
 */
public class ChatModelLlmClient implements LlmClient {

    private final String providerId;
    private final String configuredModel;
    private final ChatModel chatModel;

    public ChatModelLlmClient(String providerId, String configuredModel, ChatModel chatModel) {
        this.providerId = providerId;
        this.configuredModel = configuredModel;
        this.chatModel = chatModel;
    }

    @Override
    public String providerId() {
        return providerId;
    }

    @Override
    public String configuredModel() {
        return configuredModel;
    }

    @Override
    public ResearchResult ask(Question question, String executionId, int repetition) {
        long started = System.nanoTime();
        try {
            ChatResponse response = chatModel.call(new Prompt(question.text()));
            long latencyMs = elapsedMs(started);

            // Anthropic (ex.: claude-sonnet-5-5) pode devolver thinking como 1ª Generation
            // com texto vazio; a resposta útil fica na última Generation com texto.
            String text = extractText(response);
            if (text == null || text.isBlank()) {
                return ResearchResult.error(
                        executionId,
                        question,
                        providerId,
                        configuredModel,
                        repetition,
                        "Resposta vazia do modelo.",
                        latencyMs
                );
            }

            String effectiveModel = configuredModel;
            Integer inputTokens = null;
            Integer outputTokens = null;

            if (response.getMetadata() != null) {
                if (response.getMetadata().getModel() != null
                        && !response.getMetadata().getModel().isBlank()) {
                    effectiveModel = response.getMetadata().getModel();
                }
                Usage usage = response.getMetadata().getUsage();
                if (usage != null) {
                    inputTokens = usage.getPromptTokens();
                    outputTokens = usage.getCompletionTokens();
                }
            }

            return ResearchResult.success(
                    executionId,
                    question,
                    providerId,
                    effectiveModel,
                    repetition,
                    text,
                    latencyMs,
                    inputTokens,
                    outputTokens
            );
        } catch (Exception ex) {
            return ResearchResult.error(
                    executionId,
                    question,
                    providerId,
                    configuredModel,
                    repetition,
                    ProviderErrorMapper.map(ex),
                    elapsedMs(started)
            );
        }
    }

    /**
     * Usa a última generation com texto não vazio (ignora blocos de thinking vazios).
     */
    static String extractText(ChatResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return null;
        }
        String lastNonBlank = null;
        for (var generation : response.getResults()) {
            if (generation == null || generation.getOutput() == null) {
                continue;
            }
            String candidate = generation.getOutput().getText();
            if (candidate != null && !candidate.isBlank()) {
                lastNonBlank = candidate;
            }
        }
        return lastNonBlank;
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
