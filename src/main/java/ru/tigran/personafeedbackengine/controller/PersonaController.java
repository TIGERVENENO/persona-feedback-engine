package ru.tigran.personafeedbackengine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.tigran.personafeedbackengine.dto.JobResponse;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationRequest;
import ru.tigran.personafeedbackengine.exception.UnauthorizedException;
import ru.tigran.personafeedbackengine.service.PersonaService;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/personas")
@Tag(name = "Personas", description = "Генерация и управление AI персонами")
@SecurityRequirement(name = "bearer-jwt")
public class PersonaController {

    private final PersonaService personaService;

    public PersonaController(PersonaService personaService) {
        this.personaService = personaService;
    }

    /**
     * Безопасно извлекает userId из SecurityContext.
     * Выкидывает UnauthorizedException если токен отсутствует или невалиден.
     *
     * @return User ID из JWT токена
     * @throws UnauthorizedException если аутентификация отсутствует
     */
    private Long extractAuthenticatedUserId() {
        return Optional
                .ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(auth -> (Long) auth.getPrincipal())
                .orElseThrow(() -> new UnauthorizedException(
                        "Missing or invalid JWT token in Authorization header",
                        "MISSING_AUTHENTICATION"
                ));
    }

    /**
     * Triggers a persona generation workflow.
     * The generated persona details will be available asynchronously as the task is processed from the queue.
     *
     * Requires JWT authentication. User ID is extracted from the JWT token in Authorization header.
     *
     * @param request persona generation request with prompt
     * @return JobResponse with persona ID and initial status
     */
    @PostMapping
    @Operation(
            summary = "Создать новую персону",
            description = "Запускает асинхронный процесс генерации AI персоны по указанному описанию. " +
                    "Генерация происходит в фоне через message queue. " +
                    "Детали персоны будут доступны после завершения обработки."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "202",
                    description = "Задача генерации персоны принята",
                    content = @Content(schema = @Schema(implementation = JobResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Неверные параметры запроса (пустой prompt или слишком длинный)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Отсутствует JWT токен в заголовке Authorization"
            )
    })
    public ResponseEntity<JobResponse> generatePersona(
            @Valid @RequestBody PersonaGenerationRequest request
    ) {
        // Extract user ID from JWT token stored in SecurityContext (safe extraction with null check)
        Long userId = extractAuthenticatedUserId();
        log.info("POST /api/v1/personas - user: {}, structured persona request", userId);

        Long personaId = personaService.startPersonaGeneration(userId, request);

        JobResponse response = new JobResponse(
                personaId,
                "GENERATING"
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
