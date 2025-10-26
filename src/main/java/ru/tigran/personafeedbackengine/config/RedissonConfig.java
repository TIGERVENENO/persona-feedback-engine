package ru.tigran.personafeedbackengine.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Конфигурация Redisson для распределенных блокировок и кеширования
 */
@Configuration
@Profile("!test")
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        String redisUrl = String.format("redis://%s:%d", redisHost, redisPort);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.useSingleServer()
                    .setAddress(redisUrl)
                    .setPassword(redisPassword)
                    .setTimeout(10000)
                    .setConnectTimeout(10000)
                    .setRetryAttempts(3)
                    .setRetryInterval(1500);
        } else {
            config.useSingleServer()
                    .setAddress(redisUrl)
                    .setTimeout(10000)
                    .setConnectTimeout(10000)
                    .setRetryAttempts(3)
                    .setRetryInterval(1500);
        }

        return Redisson.create(config);
    }
}
