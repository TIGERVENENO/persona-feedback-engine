package ru.tigran.personafeedbackengine.controller;

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
import ru.tigran.personafeedbackengine.service.PersonaService;

@Slf4j
@RestController
@RequestMapping("/api/v1/personas")
public class PersonaController {

    private final PersonaService personaService;

    public PersonaController(PersonaService personaService) {
        this.personaService = personaService;
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
    public ResponseEntity<JobResponse> generatePersona(
            @Valid @RequestBody PersonaGenerationRequest request
    ) {
        // Extract user ID from JWT token stored in SecurityContext
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("POST /api/v1/personas - user: {}, prompt length: {}", userId, request.prompt().length());

        Long personaId = personaService.startPersonaGeneration(userId, request);

        JobResponse response = new JobResponse(
                personaId,
                "GENERATING"
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
