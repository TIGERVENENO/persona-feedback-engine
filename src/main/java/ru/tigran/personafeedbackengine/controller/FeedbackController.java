package ru.tigran.personafeedbackengine.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.tigran.personafeedbackengine.dto.FeedbackSessionRequest;
import ru.tigran.personafeedbackengine.dto.FeedbackSessionResponse;
import ru.tigran.personafeedbackengine.dto.JobResponse;
import ru.tigran.personafeedbackengine.service.FeedbackService;
import ru.tigran.personafeedbackengine.service.FeedbackQueryService;

@Slf4j
@RestController
@RequestMapping("/api/v1/feedback-sessions")
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
     * @param userId user ID from X-User-Id header
     * @param request feedback session request with product and persona IDs
     * @return JobResponse with session ID and initial status
     */
    @PostMapping
    public ResponseEntity<JobResponse> startFeedbackSession(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody FeedbackSessionRequest request
    ) {
        log.info("POST /api/v1/feedback-sessions - user: {}, products: {}, personas: {}",
                userId, request.productIds().size(), request.personaIds().size());

        Long sessionId = feedbackService.startFeedbackSession(userId, request);

        JobResponse response = new JobResponse(
                sessionId,
                "PENDING"
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Polls for feedback session status and results.
     * Returns the current session status and all associated feedback results.
     *
     * @param userId user ID from X-User-Id header
     * @param sessionId feedback session ID
     * @return FeedbackSessionResponse with status and feedback results
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<FeedbackSessionResponse> getFeedbackSession(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long sessionId
    ) {
        log.info("GET /api/v1/feedback-sessions/{} - user: {}", sessionId, userId);

        FeedbackSessionResponse response = feedbackQueryService.getFeedbackSessionCached(userId, sessionId);

        return ResponseEntity.ok(response);
    }
}
