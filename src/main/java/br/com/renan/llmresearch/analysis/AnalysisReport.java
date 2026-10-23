package br.com.renan.llmresearch.analysis;

import java.nio.file.Path;
import java.util.List;

/**
 * Relatório agregado a partir de {@code results.jsonl}.
 *
 * @author Renan Celso
 */
public record AnalysisReport(
        Path sourceDirectory,
        int totalRows,
        int successRows,
        int errorRows,
        List<ProviderMetric> providerMetrics,
        List<PairAsymmetry> pairAsymmetries,
        List<InterModelPair> interModelPairs,
        List<QuestionAgreement> questionAgreements
) {

    public record ProviderMetric(
            String provider,
            String model,
            int calls,
            int successes,
            int errors,
            double successRate,
            Double avgLatencyMs,
            Double medianLatencyMs,
            Double avgResponseChars,
            Double avgResponseWords,
            Double avgTypeTokenRatio,
            Double avgInputTokens,
            Double avgOutputTokens
    ) {
    }

    public record PairAsymmetry(
            String provider,
            String model,
            int repetition,
            int lulaQuestionId,
            int bolsonaroQuestionId,
            int lulaChars,
            int bolsonaroChars,
            double lengthRatioLulaOverBolsonaro,
            int lulaWords,
            int bolsonaroWords,
            double lulaTypeTokenRatio,
            double bolsonaroTypeTokenRatio,
            double jaccard,
            int bolsonaroMentionsInLula,
            int lulaMentionsInBolsonaro,
            Long lulaLatencyMs,
            Long bolsonaroLatencyMs
    ) {
    }

    public record InterModelPair(
            int questionId,
            String question,
            String providerA,
            String modelA,
            String providerB,
            String modelB,
            double jaccard,
            int charDiffAbs
    ) {
    }

    public record QuestionAgreement(
            int questionId,
            String question,
            int providerCount,
            double meanPairwiseJaccard,
            double minPairwiseJaccard,
            double maxPairwiseJaccard
    ) {
    }
}
