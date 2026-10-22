package br.com.renan.llmresearch.provider;

import br.com.renan.llmresearch.model.Question;
import br.com.renan.llmresearch.model.ResearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsula um {@link LlmClient} com retentativas automáticas em falhas transitórias.
 *
 * @author Renan Celso
 */
public class RetryingLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(RetryingLlmClient.class);

    private final LlmClient delegate;
    private final int maxAttempts;
    private final long retryDelayMs;

    public RetryingLlmClient(LlmClient delegate, int maxAttempts, long retryDelayMs) {
        this.delegate = delegate;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMs = Math.max(0L, retryDelayMs);
    }

    @Override
    public String providerId() {
        return delegate.providerId();
    }

    @Override
    public String configuredModel() {
        return delegate.configuredModel();
    }

    // 429 persistente (quota free tier) quase nunca recupera com waits longas —
    // 2 tentativas curtas bastam; o orquestrador pula o restante do provedor.
    private static final int RATE_LIMIT_MAX_ATTEMPTS = 2;
    private static final long RATE_LIMIT_BASE_DELAY_MS = 8_000L;
    private static final long RATE_LIMIT_MAX_DELAY_MS = 16_000L;

    @Override
    public ResearchResult ask(Question question, String executionId, int repetition) {
        ResearchResult lastResult = null;
        int attemptsBudget = maxAttempts;

        for (int attempt = 1; attempt <= attemptsBudget; attempt++) {
            lastResult = delegate.ask(question, executionId, repetition);

            if (lastResult.isSuccess()) {
                if (attempt > 1) {
                    log.info(
                            "Provedor {} pergunta {} obteve sucesso na tentativa {}/{}.",
                            providerId(),
                            question.id(),
                            attempt,
                            attemptsBudget
                    );
                }
                return lastResult;
            }

            if (isRateLimited(lastResult)) {
                attemptsBudget = RATE_LIMIT_MAX_ATTEMPTS;
            }

            if (!isRetryable(lastResult) || attempt == attemptsBudget) {
                if (attempt > 1) {
                    log.warn(
                            "Provedor {} pergunta {} falhou após {} tentativa(s): {}",
                            providerId(),
                            question.id(),
                            attempt,
                            lastResult.error()
                    );
                }
                return withAttemptInfo(lastResult, attempt);
            }

            long delay = delayForAttempt(lastResult, attempt);
            log.warn(
                    "Provedor {} pergunta {} falhou na tentativa {}/{}: {}. Nova tentativa em {} ms.",
                    providerId(),
                    question.id(),
                    attempt,
                    attemptsBudget,
                    lastResult.error(),
                    delay
            );
            sleepQuietly(delay);
        }

        return lastResult;
    }

    static boolean isRetryable(ResearchResult result) {
        if (result == null || result.isSuccess()) {
            return false;
        }
        String error = result.error();
        if (error == null || error.isBlank()) {
            return true;
        }
        String lower = error.toLowerCase();
        // Erros permanentes não devem ser retentados
        if (lower.contains("401")
                || lower.contains("403")
                || lower.contains("404")
                || lower.contains("credenciais")
                || lower.contains("não autorizad")
                || lower.contains("nao autorizad")
                || lower.contains("acesso negado")
                || lower.contains("modelo inexistente")
                || lower.contains("não encontrado")
                || lower.contains("nao encontrado")
                || lower.contains("sem api key")
                || lower.contains("sem modelo")
                || lower.contains("sem base-url")
                || lower.contains("configuração ausente")
                || lower.contains("configuracao ausente")
                || lower.contains("incorrect api key")) {
            return false;
        }
        return true;
    }

    public static boolean isRateLimited(ResearchResult result) {
        if (result == null || result.error() == null) {
            return false;
        }
        String lower = result.error().toLowerCase();
        return lower.contains("429")
                || lower.contains("limite de requisições")
                || lower.contains("limite de requisicoes")
                || lower.contains("rate limit")
                || lower.contains("too many requests");
    }

    private long delayForAttempt(ResearchResult result, int attempt) {
        // retryDelayMs=0 desliga espera (útil em testes)
        if (retryDelayMs == 0L) {
            return 0L;
        }
        if (isRateLimited(result)) {
            // Free tiers (ex.: Mistral): 15s → 30s → 60s (cap)
            long delay = RATE_LIMIT_BASE_DELAY_MS * (1L << Math.min(attempt - 1, 3));
            return Math.min(delay, RATE_LIMIT_MAX_DELAY_MS);
        }
        return retryDelayMs;
    }

    private ResearchResult withAttemptInfo(ResearchResult result, int attemptsUsed) {
        if (result == null || attemptsUsed <= 1 || result.error() == null) {
            return result;
        }
        String suffix = " (após " + attemptsUsed + " tentativas)";
        if (result.error().contains("tentativas)")) {
            return result;
        }
        return new ResearchResult(
                result.executionId(),
                result.questionId(),
                result.question(),
                result.provider(),
                result.model(),
                result.repetition(),
                result.response(),
                result.status(),
                result.error() + suffix,
                result.timestamp(),
                result.latencyMs(),
                result.inputTokens(),
                result.outputTokens()
        );
    }

    private static void sleepQuietly(long delayMs) {
        if (delayMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
