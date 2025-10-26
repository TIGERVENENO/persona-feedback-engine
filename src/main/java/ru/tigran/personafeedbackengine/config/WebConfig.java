package ru.tigran.personafeedbackengine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Конфигурация для web (CORS, LoggingFilter)
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String allowedOrigins;

    /**
     * Конфигурация CORS для разрешения запросов с фронтенда
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600)
                .exposedHeaders("X-Total-Count", "X-Page-Number", "X-Page-Size");

        registry.addMapping("/actuator/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    /**
     * Логирование всех запросов и ответов
     */
    @Bean
    public OncePerRequestFilter loggingFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain
            ) throws ServletException, IOException {
                long startTime = System.currentTimeMillis();
                String method = request.getMethod();
                String uri = request.getRequestURI();
                String queryString = request.getQueryString();

                try {
                    filterChain.doFilter(request, response);
                } finally {
                    long duration = System.currentTimeMillis() - startTime;
                    int status = response.getStatus();

                    // Логируем все запросы, но в разных уровнях
                    if (status >= 400) {
                        log.warn("Request: {} {} - Status: {} - Duration: {}ms - Query: {}",
                            method, uri, status, duration, queryString);
                    } else if (duration > 1000) {
                        log.info("Request: {} {} - Status: {} - Duration: {}ms (slow)",
                            method, uri, status, duration);
                    } else {
                        log.debug("Request: {} {} - Status: {} - Duration: {}ms",
                            method, uri, status, duration);
                    }
                }
            }
        };
    }
}
