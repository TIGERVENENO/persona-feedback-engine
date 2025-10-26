package ru.tigran.personafeedbackengine.config;

import io.github.resilience4j.retry.RetryRegistry;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Тестовая конфигурация для мокирования Redis-зависимостей и Resilience4j
 * Используется при запуске интеграционных тестов
 */
@TestConfiguration
public class TestConfig {

    /**
     * Мокирует RedisConnectionFactory для тестирования без реального Redis сервера
     * @return Mock RedisConnectionFactory
     */
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    /**
     * Мокирует RedisTemplate для тестирования без реального Redis сервера
     * @return Mock RedisTemplate
     */
    @Bean
    @Primary
    public RedisTemplate<String, String> redisTemplate() {
        RedisTemplate<String, String> template = Mockito.mock(RedisTemplate.class);
        template.setConnectionFactory(redisConnectionFactory());
        return template;
    }

    /**
     * Мокирует RedissonClient для тестирования без реального Redis сервера
     * @return Mock RedissonClient
     */
    @Bean
    @Primary
    public RedissonClient redissonClient() {
        return Mockito.mock(RedissonClient.class);
    }

    /**
     * Provide PasswordEncoder bean (normally from SecurityConfig)
     * @return BCryptPasswordEncoder bean
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Мокирует RetryRegistry для тестирования без Resilience4j конфигурации
     * @return Mock RetryRegistry
     */
    @Bean
    @Primary
    public RetryRegistry retryRegistry() {
        return Mockito.mock(RetryRegistry.class);
    }
}
