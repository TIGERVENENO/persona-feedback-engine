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
import org.springframework.web.bind.annotation.*;
import ru.tigran.personafeedbackengine.dto.*;
import ru.tigran.personafeedbackengine.exception.UnauthorizedException;
import ru.tigran.personafeedbackengine.service.PersonaService;
import java.util.*;
import java.util.stream.Collectors;

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
     * @param request persona generation request with demographic and psychographic parameters
     * @return JobResponse with persona ID and initial status
     */
    @PostMapping
    @Operation(
            summary = "Создать новых персон",
            description = "Запускает асинхронный процесс генерации множественных AI персон по указанным характеристикам. " +
                    "Генерация происходит в фоне через message queue. " +
                    "Детали персон будут доступны после завершения обработки. " +
                    "По умолчанию создаёт 6 персон, это можно изменить в параметре 'count'."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "202",
                    description = "Задача генерации персон принята",
                    content = @Content(schema = @Schema(implementation = JobResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Неверные параметры запроса"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Отсутствует JWT токен в заголовке Authorization"
            )
    })
    public ResponseEntity<JobResponse> generatePersonas(
            @Valid @RequestBody PersonaGenerationRequest request
    ) {
        Long userId = extractAuthenticatedUserId();
        log.info("POST /api/v1/personas - user: {}, count: {}, country: {}, activitySphere: {}",
                userId, request.count(), request.country(), request.activitySphere());

        List<Long> personaIds = personaService.startBatchPersonaGenerationWithPromptBuilder(userId, request);

        log.info("Successfully triggered batch persona generation for user {}: {} personas (IDs: {})",
                userId, personaIds.size(), personaIds);

        JobResponse response = new JobResponse(personaIds, "GENERATING");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Get all enum values for persona creation dropdowns.
     * No authentication required.
     *
     * @return EnumValuesResponse with all available enum options
     */
    @GetMapping("/enums")
    @Operation(
            summary = "Получить допустимые значения enum'ов",
            description = "Возвращает все возможные значения для enum полей при создании персоны. " +
                    "Используется для заполнения выпадающих списков на фронте."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Список всех enum значений"
            )
    })
    public ResponseEntity<EnumValuesResponse> getEnumValues() {
        log.info("GET /api/v1/personas/enums");

        List<EnumValueDTO> genders = Arrays.stream(Gender.values())
                .map(g -> new EnumValueDTO(g.getValue(), g.getDisplayName()))
                .toList();

        List<EnumValueDTO> countries = Arrays.stream(Country.values())
                .map(c -> new EnumValueDTO(c.getCode(), c.getDisplayName()))
                .toList();

        List<EnumValueDTO> activitySpheres = Arrays.stream(ActivitySphere.values())
                .map(a -> new EnumValueDTO(a.getValue(), a.getDisplayName()))
                .toList();

        EnumValuesResponse response = new EnumValuesResponse(genders, countries, activitySpheres);
        return ResponseEntity.ok(response);
    }

    /**
     * Search for existing personas by characteristics.
     * Requires JWT authentication.
     *
     * @param country ISO country code (optional)
     * @param city city name (optional)
     * @param gender gender (optional)
     * @param activitySphere activity sphere (optional)
     * @return List of matching personas
     */
    @GetMapping("/search")
    @Operation(
            summary = "Поиск существующих персон",
            description = "Ищет существующие персоны по указанным характеристикам. " +
                    "Используется для переиспользования уже созданных персон с похожими параметрами. " +
                    "Требует JWT аутентификацию."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Список найденных персон"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Отсутствует JWT токен"
            )
    })
    public ResponseEntity<List<PersonaResponse>> searchPersonas(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String activitySphere
    ) {
        Long userId = extractAuthenticatedUserId();
        log.info("GET /api/v1/personas/search - user: {}, country: {}, city: {}, gender: {}, activitySphere: {}",
                userId, country, city, gender, activitySphere);

        List<PersonaResponse> personas = personaService.searchPersonas(userId, country, city, gender, activitySphere);
        return ResponseEntity.ok(personas);
    }

    /**
     * Get a specific persona by ID.
     * Requires JWT authentication and ownership verification.
     *
     * @param personaId persona ID
     * @return PersonaResponse with complete persona information
     */
    @GetMapping("/{personaId}")
    @Operation(
            summary = "Получить персону по ID",
            description = "Возвращает полную информацию о персоне, включая все демографические, " +
                    "психографические и AI-сгенерированные данные. " +
                    "Требует JWT аутентификацию и подтверждение принадлежности персоны пользователю."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Информация о персоне"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Отсутствует JWT токен"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Персона принадлежит другому пользователю"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Персона не найдена"
            )
    })
    public ResponseEntity<PersonaResponse> getPersona(
            @PathVariable Long personaId
    ) {
        Long userId = extractAuthenticatedUserId();
        log.info("GET /api/v1/personas/{} - user: {}", personaId, userId);

        PersonaResponse persona = personaService.getPersonaResponse(userId, personaId);
        return ResponseEntity.ok(persona);
    }
}
