package br.com.renan.llmresearch.model;

import java.time.LocalDateTime;

/**
 * Resultado padronizado de uma chamada a um modelo.
 *
 * @author Renan Celso
 */
public record ResearchResult(
        String executionId,
        int questionId,
        String question,
        String provider,
        String model,
        int repetition,
        String response,
        String status,
        String error,
        LocalDateTime timestamp,
        Long latencyMs,
        Integer inputTokens,
        Integer outputTokens
) {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_ERROR = "ERROR";

    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    public static ResearchResult success(
            String executionId,
            Question question,
            String provider,
            String model,
            int repetition,
            String response,
            long latencyMs,
            Integer inputTokens,
            Integer outputTokens
    ) {
        return new ResearchResult(
                executionId,
                question.id(),
                question.text(),
                provider,
                model,
                repetition,
                response,
                STATUS_SUCCESS,
                null,
                LocalDateTime.now(),
                latencyMs,
                inputTokens,
                outputTokens
        );
    }

    public static ResearchResult error(
            String executionId,
            Question question,
            String provider,
            String model,
            int repetition,
            String errorMessage,
            Long latencyMs
    ) {
        return new ResearchResult(
                executionId,
                question.id(),
                question.text(),
                provider,
                model,
                repetition,
                null,
                STATUS_ERROR,
                errorMessage,
                LocalDateTime.now(),
                latencyMs,
                null,
                null
        );
    }
}
