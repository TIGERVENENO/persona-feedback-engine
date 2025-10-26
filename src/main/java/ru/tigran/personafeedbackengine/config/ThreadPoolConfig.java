package ru.tigran.personafeedbackengine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Конфигурация thread pools для bulkhead pattern (изоляция ресурсов)
 * Разделяет обработку персон и feedback на отдельные потоки для предотвращения взаимного блокирования
 */
@Slf4j
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    /**
     * Отдельный executor для задач генерации персон
     * Максимум 10 потоков, очередь из 50 задач
     */
    @Bean(name = "personaGenerationExecutor")
    public Executor personaGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("persona-gen-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        executor.setRejectedExecutionHandler((r, exec) ->
            log.warn("Persona generation task rejected: queue is full")
        );

        return executor;
    }

    /**
     * Отдельный executor для задач генерации feedback
     * Максимум 20 потоков, очередь из 100 задач
     */
    @Bean(name = "feedbackGenerationExecutor")
    public Executor feedbackGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("feedback-gen-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        executor.setRejectedExecutionHandler((r, exec) ->
            log.warn("Feedback generation task rejected: queue is full")
        );

        return executor;
    }
}
