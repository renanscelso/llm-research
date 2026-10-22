package br.com.renan.llmresearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * @author Renan Celso
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LlmResearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmResearchApplication.class, args);
    }
}