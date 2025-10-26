package ru.tigran.personafeedbackengine.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.tigran.personafeedbackengine.security.JwtAuthenticationFilter;

/**
 * Spring Security configuration for JWT-based authentication.
 * Uses stateless session management (no cookies/sessions).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Password encoder using BCrypt.
     * BCrypt automatically handles salting and hashing with work factor 10.
     *
     * @return BCryptPasswordEncoder bean
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Authentication manager for user authentication.
     *
     * @param authConfig Authentication configuration
     * @return AuthenticationManager bean
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Security filter chain configuration.
     * - Disables CSRF (not needed for stateless JWT auth)
     * - Sets session creation policy to stateless
     * - Adds JWT authentication filter
     * - Configures public endpoints (/api/v1/auth/*)
     * - Requires authentication for all other endpoints
     *
     * @param http HTTP security configuration
     * @return Configured SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())  // Disable CSRF for stateless JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints for authentication
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/metrics/**", "/actuator/prometheus").permitAll()
                        // All other API endpoints require authentication
                        .requestMatchers("/api/v1/**").authenticated()
                        // Other requests are allowed
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
