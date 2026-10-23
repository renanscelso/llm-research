package br.com.renan.llmresearch.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextMetricsTest {

    @Test
    void jaccardIsOneForIdenticalTexts() {
        assertEquals(1d, TextMetrics.jaccard("Saúde pública no Brasil", "Saúde pública no Brasil"), 1e-9);
    }

    @Test
    void jaccardIsZeroWhenNoOverlap() {
        assertEquals(0d, TextMetrics.jaccard("abacaxi banana", "computador teclado"), 1e-9);
    }

    @Test
    void normalizeRemovesAccents() {
        assertEquals("saude", TextMetrics.normalize("Saúde"));
    }

    @Test
    void countMentionsFindsNormalizedTerms() {
        String text = "Bolsonaro e Jair falaram de Lula.";
        assertEquals(2, TextMetrics.countMentions(text, "bolsonaro", "jair"));
        assertEquals(1, TextMetrics.countMentions(text, "lula"));
    }

    @Test
    void typeTokenRatioBetweenZeroAndOne() {
        double ttr = TextMetrics.typeTokenRatio("casa casa rua parque");
        assertTrue(ttr > 0d && ttr <= 1d);
    }
}
