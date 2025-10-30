package ru.tigran.personafeedbackengine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Валидатор JWT secret key при старте приложения.
 * Предотвращает запуск приложения с небезопасным или дефолтным ключом.
 * 
 * SECURITY FIX: CRITICAL-2 - Слабый JWT Secret
 * Проверяет что JWT secret достаточно надёжен для использования.
 */
@Slf4j
@Component
public class JwtSecretValidator implements ApplicationRunner {

    @Value("${app.jwt.secret-key}")
    private String jwtSecretKey;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private final Environment environment;

    private static final int MINIMUM_KEY_LENGTH = 32;
    private static final int RECOMMENDED_KEY_LENGTH = 64;
    
    private static final String[] FORBIDDEN_KEYS = {
            "your-secret-key-please-change-this-in-production-at-least-32-chars",
            "your-secret-key-please-change-this-in-docker-env",
            "your-generated-jwt-secret-key-here",
            "your_jwt_secret_key_here",
            "secret",
            "changeme",
            "test",
            "password",
            "123456",
            "admin",
            "default"
    };

    private static final String DEV_DEFAULT_KEY = "dev-secret-key-only-for-local-development-change-in-production";

    public JwtSecretValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Validating JWT secret key configuration for profile: {}", activeProfile);

        // Проверка на null или пустое значение
        if (jwtSecretKey == null || jwtSecretKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "JWT secret key is not configured! " +
                    "Set environment variable JWT_SECRET_KEY or configure app.jwt.secret-key property. " +
                    "Generate with: openssl rand -base64 32"
            );
        }

        // Проверка на минимальную длину (для HMAC-SHA256 требуется минимум 32 символа)
        if (jwtSecretKey.length() < MINIMUM_KEY_LENGTH) {
            throw new IllegalStateException(
                    String.format(
                            "JWT secret key is too short! Minimum required: %d characters, got: %d. " +
                            "For HMAC-SHA256, use at least 32 characters (256 bits). " +
                            "Generate with: openssl rand -base64 32",
                            MINIMUM_KEY_LENGTH, jwtSecretKey.length()
                    )
            );
        }

        // Проверка на дефолтное значение для dev (warning, не error)
        if (DEV_DEFAULT_KEY.equals(jwtSecretKey)) {
            if (isProductionProfile()) {
                throw new IllegalStateException(
                        "⛔ SECURITY ERROR: Cannot use DEFAULT JWT secret in PRODUCTION profile! " +
                        "This is a critical security vulnerability. " +
                        "Must set unique JWT_SECRET_KEY environment variable. " +
                        "Generate with: openssl rand -base64 32"
                );
            }
            log.warn(
                    "⚠️  WARNING: Using DEFAULT JWT secret key for local development! " +
                    "This is NOT secure for production. " +
                    "Set JWT_SECRET_KEY environment variable with a strong random key. " +
                    "Generate with: openssl rand -base64 32"
            );
            return;
        }

        // Проверка на дефолтные или небезопасные значения
        for (String forbiddenKey : FORBIDDEN_KEYS) {
            if (jwtSecretKey.equalsIgnoreCase(forbiddenKey) || jwtSecretKey.contains(forbiddenKey)) {
                throw new IllegalStateException(
                        "JWT secret key contains forbidden/placeholder value: '" + forbiddenKey + "'. " +
                        "Must use a strong random key! Generate with: openssl rand -base64 32"
                );
            }
        }

        // Предупреждение если ключ менее рекомендуемой длины
        if (jwtSecretKey.length() < RECOMMENDED_KEY_LENGTH) {
            log.warn(
                    "⚠️  JWT secret key length is {}, recommended at least {} characters for maximum security",
                    jwtSecretKey.length(), RECOMMENDED_KEY_LENGTH
            );
        }

        // Предупреждение если используется простой ключ в production
        if (isProductionProfile() && !hasGoodEntropy(jwtSecretKey)) {
            log.warn(
                    "⚠️  JWT secret key appears to have low entropy. " +
                    "For production, use: openssl rand -base64 64 (for 512-bit entropy)"
            );
        }

        log.info(
                "✓ JWT secret key validation passed " +
                "(length: {} characters, profile: {})",
                jwtSecretKey.length(), activeProfile
        );
    }

    private boolean isProductionProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Простая проверка entropy - ищет наличие различных типов символов.
     * Хороший random ключ должен содержать буквы, цифры и спецсимволы.
     */
    private boolean hasGoodEntropy(String key) {
        boolean hasLower = key.matches(".*[a-z].*");
        boolean hasUpper = key.matches(".*[A-Z].*");
        boolean hasDigit = key.matches(".*\d.*");
        boolean hasSpecial = key.matches(".*[^a-zA-Z0-9].*");

        // Для base64 может не быть спецсимволов, поэтому требуем минимум 3 из 4
        int count = (hasLower ? 1 : 0) + (hasUpper ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
        return count >= 3;
    }
}
