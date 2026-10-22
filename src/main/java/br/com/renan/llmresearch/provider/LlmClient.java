package br.com.renan.llmresearch.provider;

import br.com.renan.llmresearch.model.Question;
import br.com.renan.llmresearch.model.ResearchResult;

/**
 * Contrato comum para chamadas aos provedores de LLM.
 *
 * @author Renan Celso
 */
public interface LlmClient {

    String providerId();

    String configuredModel();

    ResearchResult ask(Question question, String executionId, int repetition);
}
