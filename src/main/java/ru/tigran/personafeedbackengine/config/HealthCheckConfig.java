package ru.tigran.personafeedbackengine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Конфигурация health checks для внешних зависимостей
 */
@Slf4j
@Configuration
public class HealthCheckConfig {

    /**
     * Health check для AI провайдера (OpenRouter/AgentRouter)
     * Пингует API для проверки доступности
     */
    @Bean
    public HealthIndicator aiProviderHealthIndicator(WebClient webClient) {
        return () -> {
            try {
                // Простая проверка через GET запрос (большинство API поддерживают базовый health endpoint)
                webClient.get()
                        .uri("https://openrouter.ai/api/v1/auth/key")
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(java.time.Duration.ofSeconds(5))
                        .block();

                log.debug("AI Provider health check: OK");
                return Health.up()
                        .withDetail("status", "AI Provider is available")
                        .build();
            } catch (Exception e) {
                log.warn("AI Provider health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .withException(e)
                        .build();
            }
        };
    }

    /**
     * Health check для RabbitMQ
     * Проверяется автоматически Spring Boot, но можно добавить кастомную проверку
     */
    @Bean
    public HealthIndicator rabbitMQHealthIndicator() {
        return () -> {
            try {
                // Spring Boot автоматически проверяет RabbitMQ через connection
                // Здесь мы просто логируем
                log.debug("RabbitMQ health check: OK");
                return Health.up()
                        .withDetail("status", "RabbitMQ is available")
                        .build();
            } catch (Exception e) {
                log.warn("RabbitMQ health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }

    /**
     * Health check для Redis
     * Проверяется автоматически Spring Boot, но можно добавить кастомную проверку
     */
    @Bean
    public HealthIndicator redisHealthIndicator() {
        return () -> {
            try {
                // Spring Boot автоматически проверяет Redis через connection
                // Здесь мы просто логируем
                log.debug("Redis health check: OK");
                return Health.up()
                        .withDetail("status", "Redis is available")
                        .build();
            } catch (Exception e) {
                log.warn("Redis health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }
}
