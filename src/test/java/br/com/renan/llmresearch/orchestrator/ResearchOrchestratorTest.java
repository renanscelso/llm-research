package br.com.renan.llmresearch.orchestrator;

import br.com.renan.llmresearch.config.ResearchProperties;
import br.com.renan.llmresearch.model.Question;
import br.com.renan.llmresearch.model.ResearchResult;
import br.com.renan.llmresearch.provider.LlmClient;
import br.com.renan.llmresearch.reader.QuestionReader;
import br.com.renan.llmresearch.writer.ResultWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchOrchestratorTest {

    @TempDir
    Path tempDir;

    @Test
    void executesProvidersQuestionsAndRepetitionsSequentially() throws Exception {
        Path csv = tempDir.resolve("questions.csv");
        Files.writeString(csv, """
                id,question
                1,"Pergunta um"
                2,"Pergunta dois"
                """, StandardCharsets.UTF_8);

        ResearchProperties properties = properties(csv, false);

        QuestionReader questionReader = new QuestionReader(properties);
        ResultWriter resultWriter = new ResultWriter(properties);

        AtomicInteger calls = new AtomicInteger();
        List<String> sequence = new ArrayList<>();

        LlmClient okClient = trackingClient("openai", "mock-model", calls, sequence, true);
        LlmClient failingClient = trackingClient("anthropic", "mock-claude", calls, sequence, false);

        ResearchOrchestrator orchestrator = new ResearchOrchestrator(
                questionReader,
                resultWriter,
                properties,
                List.of(failingClient, okClient)
        );

        ResearchOrchestrator.ResearchSummary summary = orchestrator.run();

        assertEquals(8, calls.get());
        assertEquals(4, summary.successCount());
        assertEquals(4, summary.errorCount());
        assertEquals(2, summary.providerCount());
        assertTrue(Files.isDirectory(summary.outputDirectory()));
        assertTrue(Files.exists(summary.outputDirectory().resolve("results.xlsx")));
        assertTrue(Files.exists(summary.outputDirectory().resolve("results.csv")));
        assertTrue(Files.exists(summary.outputDirectory().resolve("results.jsonl")));
        assertTrue(Files.exists(summary.outputDirectory().resolve("results.json")));
        assertEquals(List.of(
                "openai-1-1", "openai-1-2", "openai-2-1", "openai-2-2",
                "anthropic-1-1", "anthropic-1-2", "anthropic-2-1", "anthropic-2-2"
        ), sequence);
    }

    @Test
    void skipsRemainingQuestionsAfterProviderRateLimit() throws Exception {
        Path csv = tempDir.resolve("questions-429.csv");
        Files.writeString(csv, """
                id,question
                1,"Pergunta um"
                2,"Pergunta dois"
                """, StandardCharsets.UTF_8);

        ResearchProperties properties = new ResearchProperties(
                new ResearchProperties.RunnerProperties(false),
                1,
                csv.toString(),
                tempDir.resolve("output-429").toString(),
                120,
                3,
                0L,
                0L,
                false,
                        0,
                Map.of()
        );

        AtomicInteger liveCalls = new AtomicInteger();
        LlmClient limited = new LlmClient() {
            @Override
            public String providerId() {
                return "mistral";
            }

            @Override
            public String configuredModel() {
                return "mistral-small-latest";
            }

            @Override
            public ResearchResult ask(Question question, String executionId, int repetition) {
                liveCalls.incrementAndGet();
                return ResearchResult.error(
                        executionId, question, providerId(), configuredModel(),
                        repetition, "Limite de requisições atingido (HTTP 429).", 5L
                );
            }
        };

        ResearchOrchestrator orchestrator = new ResearchOrchestrator(
                new QuestionReader(properties),
                new ResultWriter(properties),
                properties,
                List.of(limited)
        );

        ResearchOrchestrator.ResearchSummary summary = orchestrator.run();

        assertEquals(1, liveCalls.get(), "só a 1ª pergunta deve chamar a API");
        assertEquals(2, summary.errorCount());
        assertEquals(0, summary.successCount());
        assertTrue(summary.results().get(1).error().contains("Pulado"));
    }

    @Test
    void runsProvidersInParallelKeepingOrderWithinEachProvider() throws Exception {
        Path csv = tempDir.resolve("questions-parallel.csv");
        Files.writeString(csv, """
                id,question
                1,"Pergunta um"
                2,"Pergunta dois"
                """, StandardCharsets.UTF_8);

        ResearchProperties properties = properties(csv, true);
        QuestionReader questionReader = new QuestionReader(properties);
        ResultWriter resultWriter = new ResultWriter(properties);

        AtomicInteger calls = new AtomicInteger();
        ConcurrentLinkedQueue<String> openaiSeq = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> anthropicSeq = new ConcurrentLinkedQueue<>();

        LlmClient openai = slowTrackingClient("openai", "mock-model", calls, openaiSeq, 40L);
        LlmClient anthropic = slowTrackingClient("anthropic", "mock-claude", calls, anthropicSeq, 20L);

        ResearchOrchestrator orchestrator = new ResearchOrchestrator(
                questionReader,
                resultWriter,
                properties,
                List.of(anthropic, openai)
        );

        long started = System.nanoTime();
        ResearchOrchestrator.ResearchSummary summary = orchestrator.run();
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

        assertEquals(4, calls.get());
        assertEquals(4, summary.successCount());
        assertEquals(0, summary.errorCount());
        // Sequencial seria ~160ms (4*40); paralelo deve ficar bem abaixo disso
        assertTrue(elapsedMs < 140, "esperado ganho com paralelo, elapsedMs=" + elapsedMs);
        assertEquals(List.of("openai-1-1", "openai-2-1"), List.copyOf(openaiSeq));
        assertEquals(List.of("anthropic-1-1", "anthropic-2-1"), List.copyOf(anthropicSeq));
    }

    private ResearchProperties properties(Path csv, boolean parallelProviders) {
        return new ResearchProperties(
                new ResearchProperties.RunnerProperties(false),
                parallelProviders ? 1 : 2,
                csv.toString(),
                tempDir.resolve(parallelProviders ? "output-parallel" : "output").toString(),
                120,
                3,
                0L,
                0L,
                parallelProviders,
                0,
                Map.of()
        );
    }

    private static LlmClient trackingClient(
            String id,
            String model,
            AtomicInteger calls,
            List<String> sequence,
            boolean success
    ) {
        return new LlmClient() {
            @Override
            public String providerId() {
                return id;
            }

            @Override
            public String configuredModel() {
                return model;
            }

            @Override
            public ResearchResult ask(Question question, String executionId, int repetition) {
                calls.incrementAndGet();
                synchronized (sequence) {
                    sequence.add(providerId() + "-" + question.id() + "-" + repetition);
                }
                if (success) {
                    return ResearchResult.success(
                            executionId, question, providerId(), configuredModel(),
                            repetition, "resposta " + question.id(), 10L, 5, 7
                    );
                }
                return ResearchResult.error(
                        executionId, question, providerId(), configuredModel(),
                        repetition, "falha simulada", 3L
                );
            }
        };
    }

    private static LlmClient slowTrackingClient(
            String id,
            String model,
            AtomicInteger calls,
            ConcurrentLinkedQueue<String> sequence,
            long sleepMs
    ) {
        return new LlmClient() {
            @Override
            public String providerId() {
                return id;
            }

            @Override
            public String configuredModel() {
                return model;
            }

            @Override
            public ResearchResult ask(Question question, String executionId, int repetition) {
                calls.incrementAndGet();
                sequence.add(providerId() + "-" + question.id() + "-" + repetition);
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return ResearchResult.success(
                        executionId, question, providerId(), configuredModel(),
                        repetition, "ok", sleepMs, 1, 1
                );
            }
        };
    }
}
