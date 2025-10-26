package ru.tigran.personafeedbackengine.config;

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
 * Тестовая конфигурация для мокирования Redis-зависимостей
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
}
