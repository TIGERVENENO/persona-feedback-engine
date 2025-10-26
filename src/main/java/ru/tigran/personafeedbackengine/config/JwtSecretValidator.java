package ru.tigran.personafeedbackengine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Валидатор JWT secret key при старте приложения.
 * Предотвращает запуск приложения с небезопасным или дефолтным ключом.
 */
@Slf4j
@Component
public class JwtSecretValidator implements ApplicationRunner {

    @Value("${app.jwt.secret-key}")
    private String jwtSecretKey;

    private static final int MINIMUM_KEY_LENGTH = 32;
    private static final String[] FORBIDDEN_KEYS = {
            "your-secret-key-please-change-this-in-production-at-least-32-chars",
            "your-secret-key-please-change-this-in-docker-env",
            "secret",
            "changeme",
            "test",
            "password",
            "123456"
    };

    @Override
    public void run(ApplicationArguments args) {
        log.info("Validating JWT secret key configuration...");

        // Проверка на null или пустое значение
        if (jwtSecretKey == null || jwtSecretKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "JWT secret key is not configured! " +
                    "Set environment variable JWT_SECRET_KEY or configure app.jwt.secret-key property."
            );
        }

        // Проверка на минимальную длину (для HMAC-SHA256 рекомендуется минимум 32 символа)
        if (jwtSecretKey.length() < MINIMUM_KEY_LENGTH) {
            throw new IllegalStateException(
                    String.format("JWT secret key is too short! Minimum length is %d characters, but got %d. " +
                            "Use a strong random key of at least 32 characters.",
                            MINIMUM_KEY_LENGTH, jwtSecretKey.length())
            );
        }

        // Проверка на дефолтные или небезопасные значения
        for (String forbiddenKey : FORBIDDEN_KEYS) {
            if (jwtSecretKey.equalsIgnoreCase(forbiddenKey) || jwtSecretKey.contains(forbiddenKey)) {
                throw new IllegalStateException(
                        "JWT secret key contains forbidden/default value: '" + forbiddenKey + "'. " +
                        "Please use a strong random key! Generate one with: " +
                        "openssl rand -base64 32"
                );
            }
        }

        log.info("✓ JWT secret key validation passed (length: {} characters)", jwtSecretKey.length());
    }
}
