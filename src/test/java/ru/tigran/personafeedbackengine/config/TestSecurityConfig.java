package ru.tigran.personafeedbackengine.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Тестовая конфигурация безопасности для @WebMvcTest
 * Инъектирует тестовую аутентификацию в все запросы
 * Позволяет избежать использования реального JwtAuthenticationFilter в тестах
 */
@TestConfiguration
@EnableWebSecurity
public class TestSecurityConfig {

    /**
     * Фильтр для инъекции тестовой аутентификации в SecurityContext
     * Инъектирует userId=1L для всех запросов
     */
    private static class TestAuthenticationFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            // Inject test authentication with userId = 1L
            Authentication authentication = new UsernamePasswordAuthenticationToken(1L, null);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    /**
     * Конфигурирует SecurityFilterChain для тестов
     * Добавляет тестовый фильтр аутентификации перед другими фильтрами
     */
    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/personas/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new TestAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
