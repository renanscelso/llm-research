package br.com.renan.llmresearch.config;

import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

/**
 * Templates de retry sem tentativas adicionais.
 *
 * @author Renan Celso
 */
public final class RetryTemplates {

    private RetryTemplates() {
    }

    /**
     * Uma única tentativa (sem retentativas automáticas).
     */
    public static RetryTemplate noRetry() {
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(RetryPolicy.withMaxRetries(0));
        return template;
    }
}
