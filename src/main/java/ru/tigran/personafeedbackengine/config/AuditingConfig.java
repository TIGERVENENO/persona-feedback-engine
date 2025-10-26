package ru.tigran.personafeedbackengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Конфигурация для поддержки аудита сущностей
 * Автоматически заполняет createdBy, updatedBy, createdAt, updatedAt, version
 */
@Configuration
@EnableJpaAuditing
public class AuditingConfig {

    /**
     * Возвращает текущего пользователя для аудита
     * В MVP используется 'system', в production можно интегрировать с Security
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            // TODO: В production интегрировать с SecurityContext для получения текущего пользователя
            // return SecurityContextHolder.getContext()
            //     .getAuthentication()
            //     .map(auth -> auth.getName())
            //     .or(() -> Optional.of("system"));

            return Optional.of("system");
        };
    }
}
