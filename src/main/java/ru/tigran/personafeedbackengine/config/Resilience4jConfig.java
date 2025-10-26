package ru.tigran.personafeedbackengine.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.tigran.personafeedbackengine.exception.AIGatewayException;

import java.time.Duration;

/**
 * Конфигурация Resilience4j для защиты от отказов AI провайдеров
 */
@Slf4j
@Configuration
public class Resilience4jConfig {

    /**
     * CircuitBreaker для защиты вызовов к AI API (OpenRouter, AgentRouter)
     * Открывается после 5 неудачных попыток в течение 30 секунд
     * Остается открытым на 20 секунд, затем переходит в HALF_OPEN для повторной проверки
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .minimumNumberOfCalls(5)
                .slidingWindowSize(10)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .recordExceptions(AIGatewayException.class, Exception.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build()
        );

        registry.getEventPublisher()
                .onEntryAdded(event -> log.info("CircuitBreaker created: {}", event.getAddedEntry().getName()))
                .onEntryRemoved(event -> log.info("CircuitBreaker removed: {}", event.getRemovedEntry().getName()))
                .onEntryReplaced(event -> log.info("CircuitBreaker replaced: {}", event.getNewEntry().getName()));

        return registry;
    }

    /**
     * CircuitBreaker для AI провайдера
     */
    @Bean
    public CircuitBreaker aiProviderCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("aiProvider");

        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn("CircuitBreaker state changed: {} -> {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()))
                .onError(event -> log.error("CircuitBreaker recorded error: {}", event.getThrowable().getMessage()))
                .onSuccess(event -> log.debug("CircuitBreaker recorded success"));

        return circuitBreaker;
    }
}
