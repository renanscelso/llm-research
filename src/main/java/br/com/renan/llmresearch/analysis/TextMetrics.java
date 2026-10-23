package br.com.renan.llmresearch.analysis;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Métricas lexicais leves para análise comparativa (sem dependências de NLP).
 *
 * @author Renan Celso
 */
final class TextMetrics {

    private static final Pattern NON_LETTER = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> STOPWORDS = Set.of(
            "a", "o", "as", "os", "um", "uma", "de", "da", "do", "das", "dos", "e", "em", "no", "na",
            "nos", "nas", "para", "por", "com", "sem", "que", "se", "ao", "à", "às", "aos", "ou",
            "como", "mais", "menos", "já", "também", "não", "sim", "é", "ser", "foi", "são", "sua",
            "seu", "suas", "seus", "lhe", "lhes", "isso", "este", "esta", "esse", "essa", "pelo",
            "pela", "pelos", "pelas", "entre", "sobre", "até", "muito", "muitos", "pode", "podem"
    );

    private TextMetrics() {
    }

    static int charCount(String text) {
        return text == null ? 0 : text.length();
    }

    static int wordCount(String text) {
        return tokenizeList(text).size();
    }

    /** Razão tipos/tokens (diversidade lexical aproximada). */
    static double typeTokenRatio(String text) {
        List<String> tokens = tokenizeList(text);
        if (tokens.isEmpty()) {
            return 0d;
        }
        long unique = tokens.stream().distinct().count();
        return unique / (double) tokens.size();
    }

    static double jaccard(String left, String right) {
        Set<String> a = new HashSet<>(tokenizeList(left));
        Set<String> b = new HashSet<>(tokenizeList(right));
        if (a.isEmpty() && b.isEmpty()) {
            return 1d;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0d;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return intersection.size() / (double) union.size();
    }

    static int countMentions(String text, String... terms) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String normalized = normalize(text);
        int total = 0;
        for (String term : terms) {
            String needle = normalize(term);
            if (needle.isBlank()) {
                continue;
            }
            int idx = 0;
            while ((idx = normalized.indexOf(needle, idx)) >= 0) {
                total++;
                idx += needle.length();
            }
        }
        return total;
    }

    static List<String> tokenizeList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = normalize(text);
        return Arrays.stream(NON_LETTER.split(normalized))
                .filter(token -> token.length() >= 2)
                .filter(token -> !STOPWORDS.contains(token))
                .collect(Collectors.toList());
    }

    static String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }
}
