package ru.tigran.personafeedbackengine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ApiKeyValidator implements ApplicationRunner {

    @Value("${app.ai.provider}")
    private String aiProvider;

    @Value("${app.openrouter.api-key}")
    private String openRouterApiKey;

    @Value("${app.agentrouter.api-key}")
    private String agentRouterApiKey;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Validating API key configuration for provider: {}", aiProvider);

        if ("openrouter".equalsIgnoreCase(aiProvider)) {
            validateApiKey("OPENROUTER_API_KEY", openRouterApiKey);
        } else if ("agentrouter".equalsIgnoreCase(aiProvider)) {
            validateApiKey("AGENTROUTER_API_KEY", agentRouterApiKey);
        } else {
            throw new IllegalStateException(
                String.format("Unknown AI provider: %s. Supported: openrouter, agentrouter", aiProvider)
            );
        }

        log.info("API key validation completed successfully");
    }

    private void validateApiKey(String envVarName, String apiKey) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.isBlank()) {
            throw new IllegalStateException(
                String.format(
                    "%s environment variable is not set! Please configure it before starting the application. " +
                    "Example: export %s=sk-or-your-actual-api-key",
                    envVarName, envVarName
                )
            );
        }

        if (apiKey.contains("YOUR_") || apiKey.contains("PLACEHOLDER")) {
            throw new IllegalStateException(
                String.format(
                    "%s contains placeholder value! Please set a real API key in environment variables. " +
                    "Example: export %s=sk-or-your-actual-api-key",
                    envVarName, envVarName
                )
            );
        }

        log.debug("API key validation passed for {}", envVarName);
    }
}
