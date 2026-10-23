package br.com.renan.llmresearch.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do modo de análise dos resultados coletados.
 *
 * @author Renan Celso
 */
@ConfigurationProperties(prefix = "research.analysis")
public record AnalysisProperties(
        boolean enabled,
        String inputDirectory,
        String outputSubdirectory
) {
    public AnalysisProperties {
        outputSubdirectory = (outputSubdirectory == null || outputSubdirectory.isBlank())
                ? "analysis"
                : outputSubdirectory.trim();
    }
}
