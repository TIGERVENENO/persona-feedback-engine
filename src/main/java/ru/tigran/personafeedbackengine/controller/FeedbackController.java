package ru.tigran.personafeedbackengine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.tigran.personafeedbackengine.dto.FeedbackSessionRequest;
import ru.tigran.personafeedbackengine.dto.FeedbackSessionResponse;
import ru.tigran.personafeedbackengine.dto.JobResponse;
import ru.tigran.personafeedbackengine.service.FeedbackService;
import ru.tigran.personafeedbackengine.service.FeedbackQueryService;

@Slf4j
@RestController
@RequestMapping("/api/v1/feedback-sessions")
@Tag(name = "Feedback Sessions", description = "Управление сессиями сбора фидбека на продукты")
@SecurityRequirement(name = "bearer-jwt")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final FeedbackQueryService feedbackQueryService;

    public FeedbackController(
            FeedbackService feedbackService,
            FeedbackQueryService feedbackQueryService
    ) {
        this.feedbackService = feedbackService;
        this.feedbackQueryService = feedbackQueryService;
    }

    /**
     * Triggers a feedback session workflow.
     * Creates a feedback session for the given products and personas.
     * Feedback generation will be processed asynchronously via the queue.
     *
     * Requires JWT authentication. User ID is extracted from the JWT token in Authorization header.
     *
     * @param request feedback session request with product and persona IDs
     * @return JobResponse with session ID and initial status
     */
    @PostMapping
    @Operation(
            summary = "Запустить сессию сбора фидбека",
            description = "Создает сессию для генерации фидбека от персон на указанные продукты. " +
                    "Генерация происходит асинхронно через message queue. " +
                    "Фидбек будет доступен для получения через GET endpoint после завершения."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "202",
                    description = "Сессия фидбека создана и обработка запущена",
                    content = @Content(schema = @Schema(implementation = JobResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Неверные параметры (пустые lists, превышены лимиты, недопустимые IDs)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Отсутствует JWT токен в заголовке Authorization"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Пользователь не владеет указанными продуктами или персонами"
            )
    })
    public ResponseEntity<JobResponse> startFeedbackSession(
            @Valid @RequestBody FeedbackSessionRequest request
    ) {
        // Extract user ID from JWT token stored in SecurityContext
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("POST /api/v1/feedback-sessions - user: {}, products: {}",
                userId, request.getProductIds().size());

        Long sessionId = feedbackService.startFeedbackSession(userId, request);

        JobResponse response = new JobResponse(
                sessionId,
                "PENDING"
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Polls for feedback session status and results with optional pagination.
     * Returns the current session status and associated feedback results.
     * If page and size parameters are provided, returns paginated results.
     * If not provided, returns all results (cached).
     *
     * Requires JWT authentication. User ID is extracted from the JWT token in Authorization header.
     *
     * @param sessionId feedback session ID
     * @param page page number (0-based), optional
     * @param size page size, optional
     * @return FeedbackSessionResponse with status and feedback results
     */
    @GetMapping("/{sessionId}")
    @Operation(
            summary = "Получить статус и результаты сессии фидбека",
            description = "Возвращает текущий статус сессии и список собранных фидбеков. " +
                    "Поддерживает опциональную pagination через query параметры page и size. " +
                    "Если page и size не указаны, возвращаются все результаты (кешируются)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Успешный ответ со статусом и результатами",
                    content = @Content(schema = @Schema(implementation = FeedbackSessionResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Отсутствует JWT токен в заголовке Authorization"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Пользователь не владеет этой сессией"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Сессия с указанным ID не найдена"
            )
    })
    public ResponseEntity<FeedbackSessionResponse> getFeedbackSession(
            @PathVariable
            @Parameter(description = "ID сессии фидбека", example = "123")
            Long sessionId,
            @RequestParam(required = false)
            @Parameter(description = "Номер страницы (0-based), опционально", example = "0")
            Integer page,
            @RequestParam(required = false)
            @Parameter(description = "Размер страницы, опционально", example = "10")
            Integer size
    ) {
        // Extract user ID from JWT token stored in SecurityContext
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("GET /api/v1/feedback-sessions/{} - user: {}, page: {}, size: {}", sessionId, userId, page, size);

        FeedbackSessionResponse response;
        if (page != null && size != null) {
            response = feedbackQueryService.getFeedbackSessionPaginated(userId, sessionId, page, size);
        } else {
            response = feedbackQueryService.getFeedbackSessionCached(userId, sessionId);
        }

        return ResponseEntity.ok(response);
    }
}
