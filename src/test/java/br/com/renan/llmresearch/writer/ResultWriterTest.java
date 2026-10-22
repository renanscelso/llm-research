package br.com.renan.llmresearch.writer;

import br.com.renan.llmresearch.config.ResearchProperties;
import br.com.renan.llmresearch.model.Question;
import br.com.renan.llmresearch.model.ResearchResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultWriterTest {

    private static final Pattern FOLDER_TIMESTAMP = Pattern.compile("\\d{8}-\\d{6}");

    @TempDir
    Path tempDir;

    @Test
    void exportsXlsxCsvAndJsonlInTimestampFolder() throws Exception {
        ResearchProperties properties = new ResearchProperties(
                new ResearchProperties.RunnerProperties(false),
                1,
                "unused.csv",
                tempDir.toString(),
                120,
                3,
                0L,
                0L,
                false,
                        0,
                Map.of()
        );

        ResultWriter writer = new ResultWriter(properties);
        Path directory = writer.beginRun("exec-123");

        assertTrue(Files.isDirectory(directory));
        assertEquals(tempDir, directory.getParent());
        assertTrue(FOLDER_TIMESTAMP.matcher(directory.getFileName().toString()).matches());

        Question question = new Question(1, "Como você avalia a atuação de Lula na saúde pública?");
        writer.write(ResearchResult.success(
                "exec-123",
                question,
                "openai",
                "gpt-4.1-mini",
                1,
                "Resposta com acentuação: ação, saúde, público.",
                123L,
                10,
                20
        ));
        writer.write(ResearchResult.error(
                "exec-123",
                new Question(2, "Pergunta dois"),
                "anthropic",
                "claude",
                1,
                "Credenciais inválidas ou não autorizadas (HTTP 401).",
                5L
        ));
        writer.close();

        Path xlsx = directory.resolve("results.xlsx");
        Path csv = directory.resolve("results.csv");
        Path jsonl = directory.resolve("results.jsonl");
        Path json = directory.resolve("results.json");
        assertTrue(Files.exists(xlsx));
        assertTrue(Files.exists(csv));
        assertTrue(Files.exists(jsonl));
        assertTrue(Files.exists(json));

        try (Workbook workbook = WorkbookFactory.create(xlsx.toFile())) {
            Sheet sheet = workbook.getSheet("Resultados");
            Row header = sheet.getRow(0);

            assertEquals("ID da execução", header.getCell(0).getStringCellValue());
            assertEquals("Resposta", header.getCell(6).getStringCellValue());
            assertEquals("Tokens de saída", header.getCell(12).getStringCellValue());

            Row first = sheet.getRow(1);
            assertTrue(first.getCell(2).getStringCellValue().contains("você"));
            assertTrue(first.getCell(6).getStringCellValue().contains("ação"));
            assertEquals("SUCCESS", first.getCell(7).getStringCellValue());

            Row second = sheet.getRow(2);
            assertEquals("ERROR", second.getCell(7).getStringCellValue());
            assertTrue(second.getCell(8).getStringCellValue().contains("401"));
        }

        String csvContent = Files.readString(csv, StandardCharsets.UTF_8);
        assertTrue(csvContent.startsWith("ID da execução,"));
        assertTrue(csvContent.contains("você"));
        assertTrue(csvContent.contains("ação"));
        assertTrue(csvContent.contains("SUCCESS"));
        assertTrue(csvContent.contains("ERROR"));

        List<String> jsonLines = Files.readAllLines(jsonl, StandardCharsets.UTF_8);
        assertEquals(2, jsonLines.size());
        assertTrue(jsonLines.get(0).contains("\"executionId\":\"exec-123\""));
        assertTrue(jsonLines.get(0).contains("\"question\":\"Como você avalia"));
        assertTrue(jsonLines.get(0).contains("ação"));
        assertTrue(jsonLines.get(0).contains("\"status\":\"SUCCESS\""));
        assertTrue(jsonLines.get(1).contains("\"status\":\"ERROR\""));
        assertTrue(jsonLines.get(1).contains("401"));

        String prettyJson = Files.readString(json, StandardCharsets.UTF_8);
        assertTrue(prettyJson.startsWith("["));
        assertTrue(prettyJson.contains("\n  {\n"));
        assertTrue(prettyJson.contains("\"executionId\": \"exec-123\""));
        assertTrue(prettyJson.contains("\"status\": \"SUCCESS\""));
        assertTrue(prettyJson.contains("\"status\": \"ERROR\""));
        assertTrue(prettyJson.contains("ação"));
        assertTrue(prettyJson.trim().endsWith("]"));
    }

    @Test
    void truncatesOversizedResponses() {
        String oversized = "a".repeat(ResultWriter.EXCEL_MAX_CELL_LENGTH + 100);
        String truncated = ResultWriter.truncateForExcel(oversized);
        assertEquals(ResultWriter.EXCEL_MAX_CELL_LENGTH, truncated.length());
        assertTrue(truncated.contains("TRUNCADO"));
    }

    @Test
    void escapesJsonSpecialCharacters() {
        String escaped = ResultWriter.escapeJson("linha1\n\"aspas\"\\fim");
        assertEquals("linha1\\n\\\"aspas\\\"\\\\fim", escaped);
    }
}
