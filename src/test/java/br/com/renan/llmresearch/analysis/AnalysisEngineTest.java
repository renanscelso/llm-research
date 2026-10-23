package br.com.renan.llmresearch.analysis;

import br.com.renan.llmresearch.model.Question;
import br.com.renan.llmresearch.model.ResearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void computesProviderPairAndInterModelMetrics() throws Exception {
        Question qLula = new Question(1, "Como você avalia a atuação de Lula na saúde pública?");
        Question qBolso = new Question(2, "Como você avalia a atuação de Bolsonaro na saúde pública?");

        List<ResearchResult> results = List.of(
                ResearchResult.success("e", qLula, "openai", "gpt", 1,
                        "Lula ampliou o SUS e o Farmácia Popular com foco na atenção básica.", 100, 10, 20),
                ResearchResult.success("e", qBolso, "openai", "gpt", 1,
                        "Bolsonaro enfrentou a pandemia com críticas ao isolamento e vacinas.", 120, 10, 22),
                ResearchResult.success("e", qLula, "gemini", "flash", 1,
                        "Lula investiu no SUS e programas de medicamentos populares.", 80, 8, 18),
                ResearchResult.success("e", qBolso, "gemini", "flash", 1,
                        "Bolsonaro gerou controvérsias na gestão da saúde durante a pandemia.", 90, 8, 19),
                ResearchResult.error("e", qLula, "mistral", "small", 1, "HTTP 429", 5L)
        );

        AnalysisReport report = new AnalysisEngine().analyze(tempDir, results);

        assertEquals(5, report.totalRows());
        assertEquals(4, report.successRows());
        assertEquals(1, report.errorRows());
        assertEquals(3, report.providerMetrics().size());
        assertEquals(2, report.pairAsymmetries().size());
        assertFalse(report.interModelPairs().isEmpty());
        assertEquals(2, report.questionAgreements().size());

        AnalysisReport.ProviderMetric mistral = report.providerMetrics().stream()
                .filter(m -> m.provider().equals("mistral"))
                .findFirst()
                .orElseThrow();
        assertEquals(0d, mistral.successRate(), 1e-9);

        Path out = new AnalysisReportWriter().write(report, tempDir.resolve("analysis"));
        assertTrue(java.nio.file.Files.isRegularFile(out.resolve("report.md")));
        assertTrue(java.nio.file.Files.isRegularFile(out.resolve("provider_metrics.csv")));
        assertTrue(java.nio.file.Files.isRegularFile(out.resolve("pair_asymmetry.csv")));
    }
}
