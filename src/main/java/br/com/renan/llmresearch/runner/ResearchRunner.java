package br.com.renan.llmresearch.runner;

import br.com.renan.llmresearch.orchestrator.ResearchOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Inicia o processamento quando a aplicação sobe.
 *
 * @author Renan Celso
 */
@Component
@ConditionalOnProperty(
        prefix = "research.runner",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ResearchRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ResearchRunner.class);

    private final ResearchOrchestrator orchestrator;

    public ResearchRunner(ResearchOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void run(String... args) {
        try {
            orchestrator.run();
        } catch (Exception ex) {
            log.error("Falha ao executar o experimento: {}", ex.getMessage());
            System.err.println("Falha ao executar o experimento: " + ex.getMessage());
            throw new IllegalStateException("Falha ao executar o experimento.", ex);
        }
    }
}
