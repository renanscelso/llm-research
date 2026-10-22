package br.com.renan.llmresearch.provider;

/**
 * Traduz exceções de provedores em mensagens objetivas, sem expor segredos.
 *
 * @author Renan Celso
 */
public final class ProviderErrorMapper {

    private ProviderErrorMapper() {
    }

    public static String map(Throwable throwable) {
        if (throwable == null) {
            return "Erro desconhecido.";
        }

        String message = firstMeaningfulMessage(throwable);
        String lower = message.toLowerCase();

        if (containsAny(lower, "401", "unauthorized", "invalid api key", "incorrect api key",
                "authentication", "invalid_api_key")) {
            return "Credenciais inválidas ou não autorizadas (HTTP 401).";
        }
        if (containsAny(lower, "403", "forbidden", "permission", "not allowed")) {
            return "Acesso negado pelo provedor (HTTP 403).";
        }
        if (containsAny(lower, "429", "rate limit", "too many requests", "quota")) {
            return "Limite de requisições atingido (HTTP 429).";
        }
        if (containsAny(lower, "402", "insufficient", "billing", "payment", "credit",
                "balance", "prepayment", "depleted")) {
            return "Créditos insuficientes ou problema de faturamento no provedor.";
        }
        if (containsAny(lower, "timeout", "timed out", "deadline")) {
            return "Timeout na chamada ao provedor.";
        }
        if (containsAny(lower, "unknown model", "model_not_found", "does not exist",
                "invalid model", "not found model")) {
            return "Modelo inexistente ou não disponível para a conta.";
        }
        if (containsAny(lower, "404", "not found")) {
            return "Endpoint ou recurso não encontrado (HTTP 404).";
        }
        if (containsAny(lower, "500", "502", "503", "504", "internal server", "bad gateway",
                "service unavailable", "gateway timeout")) {
            return "Erro interno no provedor (HTTP 5xx).";
        }
        if (containsAny(lower, "connection", "network", "unreachable", "unknown host",
                "nodename nor servname", "name or service not known", "resolve",
                "connect timed out", "failed to connect", "request failed")) {
            return "Falha de rede/DNS ao contactar o provedor (host inacessível).";
        }
        if (containsAny(lower, "json", "deserialize", "serialize", "parse")) {
            return "Erro de serialização/desserialização da resposta.";
        }
        if (containsAny(lower, "empty", "blank response", "no content")) {
            return "Resposta vazia do modelo.";
        }

        return sanitize(message);
    }

    private static String firstMeaningfulMessage(Throwable throwable) {
        Throwable current = throwable;
        String fallback = throwable.getClass().getSimpleName();
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage().trim();
            }
            current = current.getCause();
        }
        return fallback;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String sanitize(String message) {
        String sanitized = message
                .replaceAll("(?i)(api[_-]?key|authorization|bearer)\\s*[:=]\\s*\\S+", "$1=REDACTED")
                .replaceAll("sk-[A-Za-z0-9_\\-]{10,}", "REDACTED");
        if (sanitized.length() > 500) {
            return sanitized.substring(0, 500) + "...";
        }
        return sanitized;
    }
}
