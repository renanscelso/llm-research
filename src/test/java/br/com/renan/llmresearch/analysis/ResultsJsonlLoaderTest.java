package br.com.renan.llmresearch.analysis;

import br.com.renan.llmresearch.model.ResearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultsJsonlLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsResultsAndPicksLatestDirectory() throws Exception {
        Path older = tempDir.resolve("20260101-120000");
        Path newer = tempDir.resolve("20261022-172142");
        Files.createDirectories(older);
        Files.createDirectories(newer);
        Files.writeString(older.resolve("results.jsonl"), sampleLine("openai", 1, "Lula", "resp A") + "\n", StandardCharsets.UTF_8);
        Files.writeString(
                newer.resolve("results.jsonl"),
                sampleLine("openai", 1, "Lula", "resp B") + "\n"
                        + sampleLine("openai", 2, "Bolsonaro", "resp C") + "\n",
                StandardCharsets.UTF_8
        );

        ResultsJsonlLoader loader = new ResultsJsonlLoader();
        Path resolved = loader.resolveInputDirectory(null, tempDir.toString());
        assertEquals(newer, resolved);

        List<ResearchResult> results = loader.load(resolved);
        assertEquals(2, results.size());
        assertEquals("resp B", results.getFirst().response());
        assertTrue(results.getFirst().isSuccess());
    }

    private static String sampleLine(String provider, int questionId, String subject, String response) {
        return "{"
                + "\"executionId\":\"e1\","
                + "\"questionId\":" + questionId + ","
                + "\"question\":\"Como você avalia a atuação de " + subject + " na saúde pública?\","
                + "\"provider\":\"" + provider + "\","
                + "\"model\":\"m1\","
                + "\"repetition\":1,"
                + "\"response\":\"" + response + "\","
                + "\"status\":\"SUCCESS\","
                + "\"error\":null,"
                + "\"timestamp\":\"2026-10-22T17:21:42\","
                + "\"latencyMs\":100,"
                + "\"inputTokens\":10,"
                + "\"outputTokens\":20"
                + "}";
    }
}
