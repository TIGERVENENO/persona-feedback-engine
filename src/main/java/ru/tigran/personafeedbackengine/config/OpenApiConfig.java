package ru.tigran.personafeedbackengine.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 configuration for Swagger UI documentation.
 * Configures JWT Bearer token authentication and API metadata.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Configures OpenAPI documentation with JWT security scheme.
     * Enables Bearer token authentication in Swagger UI.
     *
     * @return OpenAPI configuration bean
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT token получен после регистрации или логина. " +
                                                "Скопируй значение 'accessToken' из ответа и вставь сюда.")
                        )
                )
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
                .info(new Info()
                        .title("Persona Feedback Engine API")
                        .description("REST API для генерации AI персон и сбора фидбека на продукты. " +
                                "Все эндпоинты (кроме /auth/*) требуют JWT аутентификацию.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Tigran")
                                .url("https://github.com/TIGERVENENO")
                        )
                );
    }
}
