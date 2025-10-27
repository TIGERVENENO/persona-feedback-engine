package ru.tigran.personafeedbackengine.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Контроллер для информации об API и эндпоинтах
 */
@RestController
@RequestMapping("/api")
@Tag(name = "API Info", description = "Информация об API и доступных эндпоинтах")
public class ApiInfoController {

    @GetMapping("/endpoints")
    public ResponseEntity<ApiEndpointsResponse> getEndpoints() {
        return ResponseEntity.ok(new ApiEndpointsResponse(
                "Persona Feedback Engine API",
                "Асинхронный сервис для генерации AI персон и сбора фидбека на продукты",
                "1.0.0",
                List.of(
                    new EndpointGroup(
                            "Аутентификация",
                            "Регистрация и вход пользователей",
                            List.of(
                                    new ApiEndpoint("POST", "/api/v1/auth/register", "Регистрация нового пользователя", false),
                                    new ApiEndpoint("POST", "/api/v1/auth/login", "Вход в систему и получение JWT токена", false)
                            )
                    ),
                    new EndpointGroup(
                            "Персоны",
                            "Управление AI персонами для сбора фидбека",
                            List.of(
                                    new ApiEndpoint("POST", "/api/v1/personas", "Запустить генерацию персоны по заданному промпту", true),
                                    new ApiEndpoint("GET", "/api/v1/personas/{personaId}", "Получить персону по ID", true),
                                    new ApiEndpoint("GET", "/api/v1/personas", "Получить все персоны пользователя", true)
                            )
                    ),
                    new EndpointGroup(
                            "Сессии фидбека",
                            "Управление сессиями сбора фидбека",
                            List.of(
                                    new ApiEndpoint("POST", "/api/v1/feedback-sessions", "Запустить сессию сбора фидбека для продуктов и персон", true),
                                    new ApiEndpoint("GET", "/api/v1/feedback-sessions/{sessionId}", "Получить результаты сессии с фидбеком", true)
                            )
                    ),
                    new EndpointGroup(
                            "Документация",
                            "Доступ к документации API",
                            List.of(
                                    new ApiEndpoint("GET", "/swagger-ui.html", "Интерактивная документация Swagger UI", false),
                                    new ApiEndpoint("GET", "/v3/api-docs", "OpenAPI документация в JSON формате", false),
                                    new ApiEndpoint("GET", "/api/endpoints", "Получить список всех эндпоинтов", false)
                            )
                    )
                )
        ));
    }

    @Getter
    @RequiredArgsConstructor
    public static class ApiEndpointsResponse {
        private final String title;
        private final String description;
        private final String version;
        private final List<EndpointGroup> groups;
    }

    @Getter
    @RequiredArgsConstructor
    public static class EndpointGroup {
        private final String name;
        private final String description;
        private final List<ApiEndpoint> endpoints;
    }

    @Getter
    @RequiredArgsConstructor
    public static class ApiEndpoint {
        private final String method;
        private final String path;
        private final String description;
        private final boolean requiresAuth;
    }
}
