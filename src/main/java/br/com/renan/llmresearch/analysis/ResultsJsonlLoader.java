package br.com.renan.llmresearch.analysis;

import br.com.renan.llmresearch.model.ResearchResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Carrega resultados de {@code results.jsonl} (pasta da execução ou última em output/).
 *
 * @author Renan Celso
 */
public class ResultsJsonlLoader {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public Path resolveInputDirectory(String configuredInput, String outputRoot) throws IOException {
        if (configuredInput != null && !configuredInput.isBlank()) {
            Path path = Path.of(configuredInput.trim());
            if (!Files.isDirectory(path)) {
                throw new IOException("Pasta de análise não encontrada: " + path.toAbsolutePath());
            }
            return path;
        }
        Path root = Path.of(outputRoot == null || outputRoot.isBlank() ? "./output" : outputRoot);
        if (!Files.isDirectory(root)) {
            throw new IOException("Diretório de saída não encontrado: " + root.toAbsolutePath());
        }
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(dir -> Files.isRegularFile(dir.resolve("results.jsonl")))
                    .max(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElseThrow(() -> new IOException(
                            "Nenhuma pasta com results.jsonl em " + root.toAbsolutePath()));
        }
    }

    public List<ResearchResult> load(Path runDirectory) throws IOException {
        Path jsonl = runDirectory.resolve("results.jsonl");
        if (!Files.isRegularFile(jsonl)) {
            throw new IOException("Arquivo não encontrado: " + jsonl.toAbsolutePath());
        }
        List<ResearchResult> results = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(jsonl, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    results.add(parseLine(line));
                } catch (Exception ex) {
                    throw new IOException("Falha ao ler results.jsonl linha " + lineNumber + ": " + ex.getMessage(), ex);
                }
            }
        }
        return List.copyOf(results);
    }

    private ResearchResult parseLine(String line) {
        JsonNode node = MAPPER.readTree(line);
        return new ResearchResult(
                text(node, "executionId"),
                node.path("questionId").asInt(),
                text(node, "question"),
                text(node, "provider"),
                text(node, "model"),
                node.path("repetition").asInt(1),
                nullableText(node, "response"),
                text(node, "status"),
                nullableText(node, "error"),
                parseTimestamp(nullableText(node, "timestamp")),
                nullableLong(node, "latencyMs"),
                nullableInteger(node, "inputTokens"),
                nullableInteger(node, "outputTokens")
        );
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString();
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asString();
        return text.isBlank() ? null : text;
    }

    private static Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asLong();
    }

    private static Integer nullableInteger(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asInt();
    }

    private static LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
