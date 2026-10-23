package br.com.renan.llmresearch.analysis;

import br.com.renan.llmresearch.config.ResearchProperties;
import br.com.renan.llmresearch.model.ResearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Orquestra carga de {@code results.jsonl}, cálculo e gravação do relatório.
 *
 * @author Renan Celso
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final ResearchProperties researchProperties;
    private final AnalysisProperties analysisProperties;
    private final ResultsJsonlLoader loader = new ResultsJsonlLoader();
    private final AnalysisEngine engine = new AnalysisEngine();
    private final AnalysisReportWriter writer = new AnalysisReportWriter();

    public AnalysisService(ResearchProperties researchProperties, AnalysisProperties analysisProperties) {
        this.researchProperties = researchProperties;
        this.analysisProperties = analysisProperties;
    }

    public Path run() throws IOException {
        Path source = loader.resolveInputDirectory(
                analysisProperties.inputDirectory(),
                researchProperties.outputDirectory()
        );
        List<ResearchResult> results = loader.load(source);
        log.info("Analisando {} registros em {}", results.size(), source.toAbsolutePath());

        AnalysisReport report = engine.analyze(source, results);
        Path analysisDir = source.resolve(analysisProperties.outputSubdirectory());
        Path written = writer.write(report, analysisDir);

        log.info(
                "Análise concluída: {} provedores, {} pares Lula/Bolsonaro, {} pares inter-modelo → {}",
                report.providerMetrics().size(),
                report.pairAsymmetries().size(),
                report.interModelPairs().size(),
                written.toAbsolutePath()
        );
        System.out.println("Relatório de análise em: " + written.toAbsolutePath());
        System.out.println("Leia: " + written.resolve("report.md").toAbsolutePath());
        return written;
    }
}
