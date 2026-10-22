package br.com.renan.llmresearch.provider;

import br.com.renan.llmresearch.model.Question;
import br.com.renan.llmresearch.model.ResearchResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryingLlmClientTest {

    @Test
    void retriesUntilSuccessWithinMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        LlmClient flaky = new LlmClient() {
            @Override
            public String providerId() {
                return "openai";
            }

            @Override
            public String configuredModel() {
                return "gpt";
            }

            @Override
            public ResearchResult ask(Question question, String executionId, int repetition) {
                int current = attempts.incrementAndGet();
                if (current < 3) {
                    return ResearchResult.error(
                            executionId, question, providerId(), configuredModel(), repetition,
                            "Erro interno no provedor (HTTP 5xx).", 5L
                    );
                }
                return ResearchResult.success(
                        executionId, question, providerId(), configuredModel(), repetition,
                        "ok", 10L, 1, 1
                );
            }
        };

        LlmClient client = new RetryingLlmClient(flaky, 3, 0L);
        ResearchResult result = client.ask(new Question(1, "Pergunta"), "exec", 1);

        assertTrue(result.isSuccess());
        assertEquals(3, attempts.get());
        assertEquals("ok", result.response());
    }

    @Test
    void stopsAfterMaxAttemptsOnRetryableFailure() {
        AtomicInteger attempts = new AtomicInteger();
        LlmClient alwaysFail = new LlmClient() {
            @Override
            public String providerId() {
                return "openai";
            }

            @Override
            public String configuredModel() {
                return "gpt";
            }

            @Override
            public ResearchResult ask(Question question, String executionId, int repetition) {
                attempts.incrementAndGet();
                return ResearchResult.error(
                        executionId, question, providerId(), configuredModel(), repetition,
                        "Timeout na chamada ao provedor.", 5L
                );
            }
        };

        LlmClient client = new RetryingLlmClient(alwaysFail, 3, 0L);
        ResearchResult result = client.ask(new Question(1, "Pergunta"), "exec", 1);

        assertEquals(ResearchResult.STATUS_ERROR, result.status());
        assertEquals(3, attempts.get());
        assertTrue(result.error().contains("após 3 tentativas"));
    }

    @Test
    void doesNotRetryNotFoundErrors() {
        AtomicInteger attempts = new AtomicInteger();
        LlmClient notFound = new LlmClient() {
            @Override
            public String providerId() {
                return "anthropic";
            }

            @Override
            public String configuredModel() {
                return "claude";
            }

            @Override
            public ResearchResult ask(Question question, String executionId, int repetition) {
                attempts.incrementAndGet();
                return ResearchResult.error(
                        executionId, question, providerId(), configuredModel(), repetition,
                        "Endpoint ou recurso não encontrado (HTTP 404).", 5L
                );
            }
        };

        LlmClient client = new RetryingLlmClient(notFound, 3, 0L);
        ResearchResult result = client.ask(new Question(1, "Pergunta"), "exec", 1);

        assertEquals(1, attempts.get());
        assertFalse(RetryingLlmClient.isRetryable(result));
    }

    @Test
    void rateLimitStopsAfterTwoAttempts() {
        AtomicInteger attempts = new AtomicInteger();
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
                attempts.incrementAndGet();
                return ResearchResult.error(
                        executionId, question, providerId(), configuredModel(), repetition,
                        "Limite de requisições atingido (HTTP 429).", 5L
                );
            }
        };

        // 429: no máximo 2 tentativas (não vale esperar minutos em quota esgotada)
        LlmClient client = new RetryingLlmClient(limited, 3, 0L);
        ResearchResult result = client.ask(new Question(1, "Pergunta"), "exec", 1);

        assertEquals(ResearchResult.STATUS_ERROR, result.status());
        assertEquals(2, attempts.get());
        assertTrue(result.error().contains("429"));
    }

    @Test
    void doesNotRetryUnauthorizedErrors() {
        AtomicInteger attempts = new AtomicInteger();
        LlmClient unauthorized = new LlmClient() {
            @Override
            public String providerId() {
                return "openai";
            }

            @Override
            public String configuredModel() {
                return "gpt";
            }

            @Override
            public ResearchResult ask(Question question, String executionId, int repetition) {
                attempts.incrementAndGet();
                return ResearchResult.error(
                        executionId, question, providerId(), configuredModel(), repetition,
                        "Credenciais inválidas ou não autorizadas (HTTP 401).", 5L
                );
            }
        };

        LlmClient client = new RetryingLlmClient(unauthorized, 3, 0L);
        ResearchResult result = client.ask(new Question(1, "Pergunta"), "exec", 1);

        assertEquals(1, attempts.get());
        assertFalse(RetryingLlmClient.isRetryable(result));
        assertTrue(result.error().contains("401"));
    }
}
