package br.com.renan.llmresearch.provider;

import br.com.renan.llmresearch.model.Question;
import br.com.renan.llmresearch.model.ResearchResult;

/**
 * Cliente usado quando o provedor está habilitado, mas a configuração está incompleta.
 *
 * @author Renan Celso
 */
public class MisconfiguredLlmClient implements LlmClient {

    private final String providerId;
    private final String configuredModel;
    private final String reason;

    public MisconfiguredLlmClient(String providerId, String configuredModel, String reason) {
        this.providerId = providerId;
        this.configuredModel = configuredModel == null ? "" : configuredModel;
        this.reason = reason;
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
        return ResearchResult.error(
                executionId,
                question,
                providerId,
                configuredModel,
                repetition,
                reason,
                0L
        );
    }
}
