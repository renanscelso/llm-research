package br.com.renan.llmresearch.reader;

import br.com.renan.llmresearch.config.ResearchProperties;
import br.com.renan.llmresearch.model.Question;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Carrega e valida perguntas a partir de CSV UTF-8.
 *
 * @author Renan Celso
 */
@Component
public class QuestionReader {

    private static final Set<String> REQUIRED_HEADERS = Set.of("id", "question");

    private final ResearchProperties properties;

    public QuestionReader(ResearchProperties properties) {
        this.properties = properties;
    }

    public List<Question> read() throws IOException {
        return read(Path.of(properties.questionsFile()));
    }

    public List<Question> read(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Caminho do CSV não informado.");
        }
        if (!Files.exists(path)) {
            throw new IOException("Arquivo CSV inexistente: " + path.toAbsolutePath());
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("Caminho informado não é um arquivo CSV válido: " + path.toAbsolutePath());
        }

        List<Question> questions = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();

        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreSurroundingSpaces(true)
                     .setTrim(true)
                     .get()
                     .parse(reader)) {

            validateHeaders(parser.getHeaderMap());

            for (var record : parser) {
                String rawId = record.get("id");
                String text = record.get("question");

                if (rawId == null || rawId.isBlank()) {
                    throw new IllegalArgumentException("ID inválido ou ausente na linha " + record.getRecordNumber());
                }

                int id;
                try {
                    id = Integer.parseInt(rawId.trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(
                            "ID inválido na linha " + record.getRecordNumber() + ": " + rawId
                    );
                }

                if (id <= 0) {
                    throw new IllegalArgumentException("ID inválido: " + id);
                }

                if (text == null || text.isBlank()) {
                    throw new IllegalArgumentException("Pergunta vazia no ID " + id);
                }

                if (!ids.add(id)) {
                    throw new IllegalArgumentException("ID duplicado: " + id);
                }

                questions.add(new Question(id, text.trim()));
            }
        }

        if (questions.isEmpty()) {
            throw new IllegalStateException("Nenhuma pergunta encontrada no CSV.");
        }

        return List.copyOf(questions);
    }

    private void validateHeaders(Map<String, Integer> headerMap) {
        if (headerMap == null || headerMap.isEmpty()) {
            throw new IllegalArgumentException("CSV sem cabeçalhos. Esperado: id,question");
        }

        Set<String> normalized = new HashSet<>();
        for (String header : headerMap.keySet()) {
            if (header != null) {
                normalized.add(header.trim().toLowerCase());
            }
        }

        for (String required : REQUIRED_HEADERS) {
            if (!normalized.contains(required)) {
                throw new IllegalArgumentException(
                        "Cabeçalho obrigatório ausente: '" + required + "'. Esperado: id,question"
                );
            }
        }
    }
}
