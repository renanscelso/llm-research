package br.com.renan.llmresearch.reader;

import br.com.renan.llmresearch.config.ResearchProperties;
import br.com.renan.llmresearch.model.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionReaderTest {

    @TempDir
    Path tempDir;

    private QuestionReader reader;

    @BeforeEach
    void setUp() {
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
        reader = new QuestionReader(properties);
    }

    @Test
    void readsQuestionsPreservingOrderAndAccents() throws Exception {
        Path csv = tempDir.resolve("questions.csv");
        Files.writeString(csv, """
                id,question
                1,"Como você avalia a atuação de Lula na saúde pública?"
                2,"Como você avalia a atuação de Bolsonaro na saúde pública?"
                """, StandardCharsets.UTF_8);

        List<Question> questions = reader.read(csv);

        assertEquals(2, questions.size());
        assertEquals(1, questions.get(0).id());
        assertTrue(questions.get(0).text().contains("você"));
        assertTrue(questions.get(0).text().contains("saúde"));
        assertEquals(2, questions.get(1).id());
        assertTrue(questions.get(1).text().contains("Bolsonaro"));
    }

    @Test
    void rejectsMissingFile() {
        Path missing = tempDir.resolve("missing.csv");
        IOExceptionLike(() -> reader.read(missing));
    }

    @Test
    void rejectsInvalidHeader() throws Exception {
        Path csv = tempDir.resolve("bad-header.csv");
        Files.writeString(csv, """
                codigo,texto
                1,pergunta
                """, StandardCharsets.UTF_8);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> reader.read(csv));
        assertTrue(ex.getMessage().contains("Cabeçalho"));
    }

    @Test
    void rejectsDuplicateIds() throws Exception {
        Path csv = tempDir.resolve("dup.csv");
        Files.writeString(csv, """
                id,question
                1,Pergunta A
                1,Pergunta B
                """, StandardCharsets.UTF_8);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> reader.read(csv));
        assertTrue(ex.getMessage().contains("duplicado"));
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        Path csv = tempDir.resolve("blank.csv");
        Files.writeString(csv, """
                id,question
                1,"   "
                """, StandardCharsets.UTF_8);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> reader.read(csv));
        assertTrue(ex.getMessage().contains("vazia"));
    }

    @Test
    void rejectsInvalidId() throws Exception {
        Path csv = tempDir.resolve("invalid-id.csv");
        Files.writeString(csv, """
                id,question
                abc,Pergunta
                """, StandardCharsets.UTF_8);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> reader.read(csv));
        assertTrue(ex.getMessage().contains("ID inválido"));
    }

    private void IOExceptionLike(ThrowingRunnable runnable) {
        Exception ex = assertThrows(Exception.class, runnable::run);
        assertTrue(ex.getMessage().contains("inexistente") || ex instanceof java.io.IOException);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
