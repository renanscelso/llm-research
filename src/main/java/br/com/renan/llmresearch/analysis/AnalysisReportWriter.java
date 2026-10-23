package br.com.renan.llmresearch.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Grava CSVs e um relatório Markdown na pasta {@code analysis/} da execução.
 *
 * @author Renan Celso
 */
public class AnalysisReportWriter {

    public Path write(AnalysisReport report, Path analysisDirectory) throws IOException {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(analysisDirectory, "analysisDirectory");
        Files.createDirectories(analysisDirectory);

        writeProviderMetrics(report, analysisDirectory.resolve("provider_metrics.csv"));
        writePairAsymmetry(report, analysisDirectory.resolve("pair_asymmetry.csv"));
        writeInterModel(report, analysisDirectory.resolve("inter_model_agreement.csv"));
        writeQuestionAgreement(report, analysisDirectory.resolve("question_agreement.csv"));
        writeMarkdown(report, analysisDirectory.resolve("report.md"));
        return analysisDirectory;
    }

    private void writeProviderMetrics(AnalysisReport report, Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
             CSVPrinter csv = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader(
                             "provider", "model", "calls", "successes", "errors", "success_rate",
                             "avg_latency_ms", "median_latency_ms", "avg_response_chars",
                             "avg_response_words", "avg_type_token_ratio",
                             "avg_input_tokens", "avg_output_tokens"
                     )
                     .get())) {
            for (AnalysisReport.ProviderMetric m : report.providerMetrics()) {
                csv.printRecord(
                        m.provider(),
                        m.model(),
                        m.calls(),
                        m.successes(),
                        m.errors(),
                        format(m.successRate()),
                        format(m.avgLatencyMs()),
                        format(m.medianLatencyMs()),
                        format(m.avgResponseChars()),
                        format(m.avgResponseWords()),
                        format(m.avgTypeTokenRatio()),
                        format(m.avgInputTokens()),
                        format(m.avgOutputTokens())
                );
            }
        }
    }

    private void writePairAsymmetry(AnalysisReport report, Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
             CSVPrinter csv = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader(
                             "provider", "model", "repetition",
                             "lula_question_id", "bolsonaro_question_id",
                             "lula_chars", "bolsonaro_chars", "length_ratio_lula_over_bolsonaro",
                             "lula_words", "bolsonaro_words",
                             "lula_ttr", "bolsonaro_ttr", "jaccard",
                             "bolsonaro_mentions_in_lula", "lula_mentions_in_bolsonaro",
                             "lula_latency_ms", "bolsonaro_latency_ms"
                     )
                     .get())) {
            for (AnalysisReport.PairAsymmetry p : report.pairAsymmetries()) {
                csv.printRecord(
                        p.provider(),
                        p.model(),
                        p.repetition(),
                        p.lulaQuestionId(),
                        p.bolsonaroQuestionId(),
                        p.lulaChars(),
                        p.bolsonaroChars(),
                        format(p.lengthRatioLulaOverBolsonaro()),
                        p.lulaWords(),
                        p.bolsonaroWords(),
                        format(p.lulaTypeTokenRatio()),
                        format(p.bolsonaroTypeTokenRatio()),
                        format(p.jaccard()),
                        p.bolsonaroMentionsInLula(),
                        p.lulaMentionsInBolsonaro(),
                        p.lulaLatencyMs() == null ? "" : p.lulaLatencyMs(),
                        p.bolsonaroLatencyMs() == null ? "" : p.bolsonaroLatencyMs()
                );
            }
        }
    }

    private void writeInterModel(AnalysisReport report, Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
             CSVPrinter csv = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader(
                             "question_id", "question",
                             "provider_a", "model_a", "provider_b", "model_b",
                             "jaccard", "char_diff_abs"
                     )
                     .get())) {
            for (AnalysisReport.InterModelPair p : report.interModelPairs()) {
                csv.printRecord(
                        p.questionId(),
                        p.question(),
                        p.providerA(),
                        p.modelA(),
                        p.providerB(),
                        p.modelB(),
                        format(p.jaccard()),
                        p.charDiffAbs()
                );
            }
        }
    }

    private void writeQuestionAgreement(AnalysisReport report, Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
             CSVPrinter csv = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader(
                             "question_id", "question", "provider_count",
                             "mean_pairwise_jaccard", "min_pairwise_jaccard", "max_pairwise_jaccard"
                     )
                     .get())) {
            for (AnalysisReport.QuestionAgreement q : report.questionAgreements()) {
                csv.printRecord(
                        q.questionId(),
                        q.question(),
                        q.providerCount(),
                        format(q.meanPairwiseJaccard()),
                        format(q.minPairwiseJaccard()),
                        format(q.maxPairwiseJaccard())
                );
            }
        }
    }

    private void writeMarkdown(AnalysisReport report, Path file) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# Relatório de análise\n\n");
        md.append("- **Fonte:** `").append(report.sourceDirectory().toAbsolutePath()).append("`\n");
        md.append("- **Linhas:** ").append(report.totalRows())
                .append(" (sucesso=").append(report.successRows())
                .append(", erro=").append(report.errorRows()).append(")\n\n");

        md.append("## Operacional por provedor\n\n");
        md.append("| Provedor | Modelo | Chamadas | Sucesso | Taxa | Latência média (ms) | Chars médios |\n");
        md.append("|---|---|---:|---:|---:|---:|---:|\n");
        for (AnalysisReport.ProviderMetric m : report.providerMetrics()) {
            md.append("| ").append(m.provider())
                    .append(" | ").append(nullSafe(m.model()))
                    .append(" | ").append(m.calls())
                    .append(" | ").append(m.successes())
                    .append(" | ").append(pct(m.successRate()))
                    .append(" | ").append(format(m.avgLatencyMs()))
                    .append(" | ").append(format(m.avgResponseChars()))
                    .append(" |\n");
        }

        md.append("\n## Assimetria Lula × Bolsonaro (mesmo provedor/repetição)\n\n");
        if (report.pairAsymmetries().isEmpty()) {
            md.append("_Nenhum par Lula/Bolsonaro com sucesso encontrado._\n");
        } else {
            md.append("| Provedor | Rep | Chars Lula | Chars Bolso | Razão L/B | Jaccard | Menções cruzadas (B em L / L em B) |\n");
            md.append("|---|---:|---:|---:|---:|---:|---:|\n");
            for (AnalysisReport.PairAsymmetry p : report.pairAsymmetries()) {
                md.append("| ").append(p.provider())
                        .append(" | ").append(p.repetition())
                        .append(" | ").append(p.lulaChars())
                        .append(" | ").append(p.bolsonaroChars())
                        .append(" | ").append(format(p.lengthRatioLulaOverBolsonaro()))
                        .append(" | ").append(format(p.jaccard()))
                        .append(" | ").append(p.bolsonaroMentionsInLula())
                        .append(" / ").append(p.lulaMentionsInBolsonaro())
                        .append(" |\n");
            }
            md.append("\n_Razão L/B > 1: resposta sobre Lula mais longa que sobre Bolsonaro. ");
            md.append("Jaccard baixo: vocabulário pouco sobreposto entre os dois textos._\n");
        }

        md.append("\n## Acordo entre modelos (Jaccard médio por pergunta)\n\n");
        if (report.questionAgreements().isEmpty()) {
            md.append("_Sem respostas de sucesso para comparar._\n");
        } else {
            md.append("| ID | Pergunta | Modelos | Jaccard médio | Mín | Máx |\n");
            md.append("|---:|---|---:|---:|---:|---:|\n");
            for (AnalysisReport.QuestionAgreement q : report.questionAgreements()) {
                md.append("| ").append(q.questionId())
                        .append(" | ").append(shorten(q.question(), 60))
                        .append(" | ").append(q.providerCount())
                        .append(" | ").append(format(q.meanPairwiseJaccard()))
                        .append(" | ").append(format(q.minPairwiseJaccard()))
                        .append(" | ").append(format(q.maxPairwiseJaccard()))
                        .append(" |\n");
            }
        }

        md.append("\n## Arquivos gerados\n\n");
        md.append("- `provider_metrics.csv`\n");
        md.append("- `pair_asymmetry.csv`\n");
        md.append("- `inter_model_agreement.csv`\n");
        md.append("- `question_agreement.csv`\n");
        md.append("- `report.md` (este arquivo)\n");

        Files.writeString(file, md.toString(), StandardCharsets.UTF_8);
    }

    private static String format(Double value) {
        if (value == null) {
            return "";
        }
        if (Double.isInfinite(value)) {
            return "inf";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String pct(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100d);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replace('|', '/').replace('\n', ' ').trim();
        if (cleaned.length() <= max) {
            return cleaned;
        }
        return cleaned.substring(0, max - 1) + "…";
    }
}
