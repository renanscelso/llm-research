package br.com.renan.llmresearch.runner;

import br.com.renan.llmresearch.analysis.AnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Executa a análise offline dos dados já coletados (sem chamar APIs de LLM).
 *
 * @author Renan Celso
 */
@Component
@Order(1)
@ConditionalOnProperty(
        prefix = "research.analysis",
        name = "enabled",
        havingValue = "true"
)
public class AnalysisRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRunner.class);

    private final AnalysisService analysisService;

    public AnalysisRunner(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Override
    public void run(String... args) {
        try {
            analysisService.run();
        } catch (Exception ex) {
            log.error("Falha na análise: {}", ex.getMessage());
            System.err.println("Falha na análise: " + ex.getMessage());
            throw new IllegalStateException("Falha na análise.", ex);
        }
    }
}
