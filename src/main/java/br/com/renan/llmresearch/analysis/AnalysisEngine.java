package br.com.renan.llmresearch.analysis;

import br.com.renan.llmresearch.model.ResearchResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * Calcula métricas operacionais, assimetria Lula/Bolsonaro e acordo entre modelos.
 *
 * @author Renan Celso
 */
public class AnalysisEngine {

    public AnalysisReport analyze(Path sourceDirectory, List<ResearchResult> results) {
        Objects.requireNonNull(sourceDirectory, "sourceDirectory");
        Objects.requireNonNull(results, "results");

        int success = (int) results.stream().filter(ResearchResult::isSuccess).count();
        int error = results.size() - success;

        return new AnalysisReport(
                sourceDirectory,
                results.size(),
                success,
                error,
                providerMetrics(results),
                pairAsymmetries(results),
                interModelPairs(results),
                questionAgreements(results)
        );
    }

    private List<AnalysisReport.ProviderMetric> providerMetrics(List<ResearchResult> results) {
        Map<String, List<ResearchResult>> byProviderModel = results.stream()
                .collect(Collectors.groupingBy(
                        r -> r.provider() + "\0" + nullToEmpty(r.model()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<AnalysisReport.ProviderMetric> metrics = new ArrayList<>();
        for (List<ResearchResult> group : byProviderModel.values()) {
            ResearchResult sample = group.getFirst();
            List<ResearchResult> ok = group.stream().filter(ResearchResult::isSuccess).toList();
            int calls = group.size();
            int successes = ok.size();
            int errors = calls - successes;

            metrics.add(new AnalysisReport.ProviderMetric(
                    sample.provider(),
                    sample.model(),
                    calls,
                    successes,
                    errors,
                    calls == 0 ? 0d : successes / (double) calls,
                    average(group.stream().map(ResearchResult::latencyMs).filter(Objects::nonNull).mapToLong(Long::longValue).boxed().toList()),
                    median(group.stream().map(ResearchResult::latencyMs).filter(Objects::nonNull).map(Long::doubleValue).sorted().toList()),
                    averageInt(ok.stream().mapToInt(r -> TextMetrics.charCount(r.response())).boxed().toList()),
                    averageInt(ok.stream().mapToInt(r -> TextMetrics.wordCount(r.response())).boxed().toList()),
                    averageDouble(ok.stream().mapToDouble(r -> TextMetrics.typeTokenRatio(r.response())).boxed().toList()),
                    average(ok.stream().map(ResearchResult::inputTokens).filter(Objects::nonNull).map(Integer::longValue).toList()),
                    average(ok.stream().map(ResearchResult::outputTokens).filter(Objects::nonNull).map(Integer::longValue).toList())
            ));
        }

        metrics.sort(Comparator
                .comparing(AnalysisReport.ProviderMetric::provider)
                .thenComparing(m -> nullToEmpty(m.model())));
        return List.copyOf(metrics);
    }

    private List<AnalysisReport.PairAsymmetry> pairAsymmetries(List<ResearchResult> results) {
        Map<String, List<ResearchResult>> byKey = results.stream()
                .filter(ResearchResult::isSuccess)
                .filter(r -> r.response() != null && !r.response().isBlank())
                .collect(Collectors.groupingBy(
                        r -> r.provider() + "\0" + nullToEmpty(r.model()) + "\0" + r.repetition(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<AnalysisReport.PairAsymmetry> pairs = new ArrayList<>();
        for (List<ResearchResult> group : byKey.values()) {
            ResearchResult lula = findSubject(group, "lula").orElse(null);
            ResearchResult bolsonaro = findSubject(group, "bolsonaro").orElse(null);
            if (lula == null || bolsonaro == null) {
                continue;
            }
            String lulaText = lula.response();
            String bolsoText = bolsonaro.response();
            int lulaChars = TextMetrics.charCount(lulaText);
            int bolsoChars = TextMetrics.charCount(bolsoText);
            double lengthRatio = bolsoChars == 0
                    ? (lulaChars == 0 ? 1d : Double.POSITIVE_INFINITY)
                    : lulaChars / (double) bolsoChars;

            pairs.add(new AnalysisReport.PairAsymmetry(
                    lula.provider(),
                    lula.model(),
                    lula.repetition(),
                    lula.questionId(),
                    bolsonaro.questionId(),
                    lulaChars,
                    bolsoChars,
                    lengthRatio,
                    TextMetrics.wordCount(lulaText),
                    TextMetrics.wordCount(bolsoText),
                    TextMetrics.typeTokenRatio(lulaText),
                    TextMetrics.typeTokenRatio(bolsoText),
                    TextMetrics.jaccard(lulaText, bolsoText),
                    TextMetrics.countMentions(lulaText, "bolsonaro", "jair"),
                    TextMetrics.countMentions(bolsoText, "lula", "luiz inacio"),
                    lula.latencyMs(),
                    bolsonaro.latencyMs()
            ));
        }

        pairs.sort(Comparator
                .comparing(AnalysisReport.PairAsymmetry::provider)
                .thenComparing(AnalysisReport.PairAsymmetry::repetition));
        return List.copyOf(pairs);
    }

    private List<AnalysisReport.InterModelPair> interModelPairs(List<ResearchResult> results) {
        Map<Integer, List<ResearchResult>> byQuestion = representativeSuccesses(results);
        List<AnalysisReport.InterModelPair> pairs = new ArrayList<>();

        for (Map.Entry<Integer, List<ResearchResult>> entry : byQuestion.entrySet()) {
            List<ResearchResult> answers = entry.getValue();
            for (int i = 0; i < answers.size(); i++) {
                for (int j = i + 1; j < answers.size(); j++) {
                    ResearchResult a = answers.get(i);
                    ResearchResult b = answers.get(j);
                    String left = a.response();
                    String right = b.response();
                    pairs.add(new AnalysisReport.InterModelPair(
                            entry.getKey(),
                            a.question(),
                            a.provider(),
                            a.model(),
                            b.provider(),
                            b.model(),
                            TextMetrics.jaccard(left, right),
                            Math.abs(TextMetrics.charCount(left) - TextMetrics.charCount(right))
                    ));
                }
            }
        }

        pairs.sort(Comparator
                .comparingInt(AnalysisReport.InterModelPair::questionId)
                .thenComparing(AnalysisReport.InterModelPair::providerA)
                .thenComparing(AnalysisReport.InterModelPair::providerB));
        return List.copyOf(pairs);
    }

    private List<AnalysisReport.QuestionAgreement> questionAgreements(List<ResearchResult> results) {
        Map<Integer, List<ResearchResult>> byQuestion = representativeSuccesses(results);
        List<AnalysisReport.InterModelPair> allPairs = interModelPairs(results);
        Map<Integer, List<AnalysisReport.InterModelPair>> pairsByQuestion = allPairs.stream()
                .collect(Collectors.groupingBy(AnalysisReport.InterModelPair::questionId, LinkedHashMap::new, Collectors.toList()));

        List<AnalysisReport.QuestionAgreement> agreements = new ArrayList<>();
        for (Map.Entry<Integer, List<ResearchResult>> entry : byQuestion.entrySet()) {
            int questionId = entry.getKey();
            List<ResearchResult> answers = entry.getValue();
            List<AnalysisReport.InterModelPair> pairs = pairsByQuestion.getOrDefault(questionId, List.of());
            DoubleSummaryStatistics stats = pairs.stream()
                    .mapToDouble(AnalysisReport.InterModelPair::jaccard)
                    .summaryStatistics();
            String question = answers.isEmpty() ? "" : answers.getFirst().question();
            agreements.add(new AnalysisReport.QuestionAgreement(
                    questionId,
                    question,
                    answers.size(),
                    pairs.isEmpty() ? 1d : stats.getAverage(),
                    pairs.isEmpty() ? 1d : stats.getMin(),
                    pairs.isEmpty() ? 1d : stats.getMax()
            ));
        }

        agreements.sort(Comparator.comparingInt(AnalysisReport.QuestionAgreement::questionId));
        return List.copyOf(agreements);
    }

    /**
     * Uma resposta de sucesso por provedor (menor repetição) para cada pergunta.
     */
    private Map<Integer, List<ResearchResult>> representativeSuccesses(List<ResearchResult> results) {
        Map<Integer, Map<String, ResearchResult>> best = new LinkedHashMap<>();
        results.stream()
                .filter(ResearchResult::isSuccess)
                .filter(r -> r.response() != null && !r.response().isBlank())
                .sorted(Comparator.comparingInt(ResearchResult::repetition))
                .forEach(r -> {
                    String key = r.provider() + "\0" + nullToEmpty(r.model());
                    best.computeIfAbsent(r.questionId(), id -> new LinkedHashMap<>())
                            .putIfAbsent(key, r);
                });

        Map<Integer, List<ResearchResult>> out = new LinkedHashMap<>();
        best.forEach((questionId, map) -> out.put(
                questionId,
                map.values().stream()
                        .sorted(Comparator.comparing(ResearchResult::provider))
                        .toList()
        ));
        return out;
    }

    private static java.util.Optional<ResearchResult> findSubject(List<ResearchResult> group, String subject) {
        String needle = TextMetrics.normalize(subject);
        return group.stream()
                .filter(r -> TextMetrics.normalize(r.question()).contains(needle))
                .findFirst();
    }

    private static Double average(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        OptionalDouble avg = values.stream().mapToLong(Long::longValue).average();
        return avg.isPresent() ? avg.getAsDouble() : null;
    }

    private static Double averageInt(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        OptionalDouble avg = values.stream().mapToInt(Integer::intValue).average();
        return avg.isPresent() ? avg.getAsDouble() : null;
    }

    private static Double averageDouble(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        OptionalDouble avg = values.stream().mapToDouble(Double::doubleValue).average();
        return avg.isPresent() ? avg.getAsDouble() : null;
    }

    private static Double median(List<Double> sorted) {
        if (sorted == null || sorted.isEmpty()) {
            return null;
        }
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2d;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
