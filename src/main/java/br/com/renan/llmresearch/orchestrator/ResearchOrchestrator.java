package br.com.renan.llmresearch.orchestrator;

import br.com.renan.llmresearch.config.ResearchProperties;
import br.com.renan.llmresearch.model.Question;
import br.com.renan.llmresearch.model.ResearchResult;
import br.com.renan.llmresearch.provider.LlmClient;
import br.com.renan.llmresearch.provider.RetryingLlmClient;
import br.com.renan.llmresearch.reader.QuestionReader;
import br.com.renan.llmresearch.writer.ResultWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controla a execução das perguntas, modelos e repetições.
 * Provedores podem rodar em paralelo; dentro de cada provedor a ordem é sequencial
 * (com pausa configurável) para reduzir rate limit.
 *
 * @author Renan Celso
 */
@Service
public class ResearchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ResearchOrchestrator.class);

    private final QuestionReader questionReader;
    private final ResultWriter resultWriter;
    private final ResearchProperties properties;
    private final List<LlmClient> llmClients;

    public ResearchOrchestrator(
            QuestionReader questionReader,
            ResultWriter resultWriter,
            ResearchProperties properties,
            List<LlmClient> llmClients
    ) {
        this.questionReader = questionReader;
        this.resultWriter = resultWriter;
        this.properties = properties;
        this.llmClients = llmClients;
    }

    public ResearchSummary run() throws Exception {
        if (properties.repetitions() < 1) {
            throw new IllegalArgumentException("O número de repetições deve ser maior que zero.");
        }

        List<Question> questions = questionReader.read();
        List<LlmClient> clients = orderedClients();

        String executionId = UUID.randomUUID().toString();
        Path outputDirectory = resultWriter.beginRun(executionId);

        boolean parallel = properties.parallelProviders() && clients.size() > 1;
        log.info("Execução {} iniciada com {} pergunta(s), {} provedor(es), {} repetição(ões), paralelo={}.",
                executionId, questions.size(), clients.size(), properties.repetitions(), parallel);
        System.out.println("ID da execução: " + executionId);
        System.out.println("Perguntas: " + questions.size());
        System.out.println("Provedores habilitados: " + clients.size());
        System.out.println("Repetições: " + properties.repetitions());
        System.out.println("Paralelo entre provedores: " + (parallel ? "sim" : "não"));
        System.out.println("Pasta de saída: " + outputDirectory.toAbsolutePath());
        System.out.println("Arquivos: results.xlsx | results.csv | results.jsonl | results.json");

        if (clients.isEmpty()) {
            System.out.println("Nenhum provedor habilitado. Configure research.providers.<nome>.enabled=true e as variáveis de ambiente.");
            resultWriter.close();
            return new ResearchSummary(executionId, questions.size(), 0, 0, 0, outputDirectory, List.of());
        }

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();
        List<ResearchResult> results = new ArrayList<>();

        try {
            if (parallel) {
                runProvidersInParallel(clients, questions, executionId, successCount, errorCount, results);
            } else {
                for (LlmClient client : clients) {
                    runProvider(client, questions, executionId, successCount, errorCount, results);
                }
            }
        } finally {
            resultWriter.close();
        }

        printSummary(
                executionId,
                questions.size(),
                clients.size(),
                successCount.get(),
                errorCount.get(),
                outputDirectory
        );
        return new ResearchSummary(
                executionId,
                questions.size(),
                clients.size(),
                successCount.get(),
                errorCount.get(),
                outputDirectory,
                List.copyOf(results)
        );
    }

    private void runProvidersInParallel(
            List<LlmClient> clients,
            List<Question> questions,
            String executionId,
            AtomicInteger successCount,
            AtomicInteger errorCount,
            List<ResearchResult> results
    ) throws Exception {
        // Uma thread por provedor: I/O-bound, virtual threads no Java 21
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<ResearchResult>>> futures = new ArrayList<>(clients.size());
            for (LlmClient client : clients) {
                Callable<List<ResearchResult>> task = () -> {
                    List<ResearchResult> providerResults = new ArrayList<>();
                    runProvider(client, questions, executionId, successCount, errorCount, providerResults);
                    return providerResults;
                };
                futures.add(executor.submit(task));
            }

            for (Future<List<ResearchResult>> future : futures) {
                try {
                    results.addAll(future.get());
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    if (cause instanceof Exception checked) {
                        throw checked;
                    }
                    throw new RuntimeException(cause);
                }
            }
        }
    }

    private void runProvider(
            LlmClient client,
            List<Question> questions,
            String executionId,
            AtomicInteger successCount,
            AtomicInteger errorCount,
            List<ResearchResult> results
    ) throws Exception {
        println("---- Provedor: " + client.providerId()
                + " | modelo configurado: " + client.configuredModel());

        boolean firstCallForProvider = true;
        questionLoop:
        for (int qIndex = 0; qIndex < questions.size(); qIndex++) {
            Question question = questions.get(qIndex);
            for (int repetition = 1; repetition <= properties.repetitions(); repetition++) {
                if (!firstCallForProvider) {
                    sleepBetweenRequests();
                }
                firstCallForProvider = false;

                println("Consultando " + client.providerId()
                        + " | pergunta " + question.id()
                        + " | repetição " + repetition);

                ResearchResult result = client.ask(question, executionId, repetition);
                recordResult(result, results, successCount, errorCount);

                if (RetryingLlmClient.isRateLimited(result)) {
                    println("Provedor " + client.providerId()
                            + " em rate limit (429) — pulando perguntas restantes deste provedor.");
                    log.warn("Circuit breaker: pulando restante de {} após 429.", client.providerId());
                    skipRemainingAfterRateLimit(
                            client,
                            questions,
                            qIndex,
                            repetition,
                            executionId,
                            results,
                            errorCount
                    );
                    break questionLoop;
                }
            }
        }
    }

    private void recordResult(
            ResearchResult result,
            List<ResearchResult> results,
            AtomicInteger successCount,
            AtomicInteger errorCount
    ) throws Exception {
        synchronized (results) {
            results.add(result);
        }
        resultWriter.write(result);

        if (result.isSuccess()) {
            successCount.incrementAndGet();
            println("OK " + result.provider() + " pergunta " + result.questionId()
                    + " (" + result.latencyMs() + " ms)");
        } else {
            errorCount.incrementAndGet();
            println("ERRO " + result.provider() + " pergunta " + result.questionId()
                    + ": " + result.error());
            log.warn("Falha em {} pergunta {} repetição {}: {}",
                    result.provider(), result.questionId(), result.repetition(), result.error());
        }
    }

    /**
     * Marca como erro as perguntas/repetições que não serão tentadas após 429 no provedor.
     */
    private void skipRemainingAfterRateLimit(
            LlmClient client,
            List<Question> questions,
            int failedQuestionIndex,
            int failedRepetition,
            String executionId,
            List<ResearchResult> results,
            AtomicInteger errorCount
    ) throws Exception {
        String skipMessage = "Pulado: rate limit (HTTP 429) anterior neste provedor.";
        for (int qIndex = failedQuestionIndex; qIndex < questions.size(); qIndex++) {
            Question question = questions.get(qIndex);
            int startRep = (qIndex == failedQuestionIndex) ? failedRepetition + 1 : 1;
            for (int repetition = startRep; repetition <= properties.repetitions(); repetition++) {
                ResearchResult skipped = ResearchResult.error(
                        executionId,
                        question,
                        client.providerId(),
                        client.configuredModel(),
                        repetition,
                        skipMessage,
                        0L
                );
                synchronized (results) {
                    results.add(skipped);
                }
                resultWriter.write(skipped);
                errorCount.incrementAndGet();
                println("PULADO " + client.providerId() + " pergunta " + question.id()
                        + " repetição " + repetition);
            }
        }
    }

    private List<LlmClient> orderedClients() {
        Map<String, LlmClient> byId = new LinkedHashMap<>();
        for (LlmClient client : llmClients) {
            byId.put(client.providerId(), client);
        }

        List<LlmClient> ordered = new ArrayList<>();
        for (String providerId : ResearchProperties.PROVIDER_ORDER) {
            LlmClient client = byId.get(providerId);
            if (client != null) {
                ordered.add(client);
            }
        }
        for (LlmClient client : llmClients) {
            if (!ResearchProperties.PROVIDER_ORDER.contains(client.providerId())
                    && ordered.stream().noneMatch(c -> c.providerId().equals(client.providerId()))) {
                ordered.add(client);
            }
        }
        return ordered;
    }

    private void sleepBetweenRequests() {
        long delayMs = properties.interRequestDelayMs();
        if (delayMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void println(String message) {
        synchronized (System.out) {
            System.out.println(message);
        }
    }

    private void printSummary(
            String executionId,
            int questionCount,
            int providerCount,
            int successCount,
            int errorCount,
            Path outputDirectory
    ) {
        System.out.println();
        System.out.println("===== Resumo da execução =====");
        System.out.println("ID: " + executionId);
        System.out.println("Perguntas: " + questionCount);
        System.out.println("Provedores: " + providerCount);
        System.out.println("Sucessos: " + successCount);
        System.out.println("Erros: " + errorCount);
        System.out.println("Pasta: " + outputDirectory.toAbsolutePath());
        System.out.println("Arquivos: results.xlsx | results.csv | results.jsonl | results.json");
        System.out.println("Experimento finalizado.");
    }

    public record ResearchSummary(
            String executionId,
            int questionCount,
            int providerCount,
            int successCount,
            int errorCount,
            Path outputDirectory,
            List<ResearchResult> results
    ) {
    }
}
