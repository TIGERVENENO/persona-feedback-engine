package ru.tigran.personafeedbackengine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationTask;
import ru.tigran.personafeedbackengine.exception.AIGatewayException;
import ru.tigran.personafeedbackengine.exception.ErrorCode;
import ru.tigran.personafeedbackengine.exception.ResourceNotFoundException;
import ru.tigran.personafeedbackengine.model.Persona;
import ru.tigran.personafeedbackengine.repository.PersonaRepository;

/**
 * Service for persona generation business logic.
 * Handles AI-driven persona generation, validation, and persistence.
 * Extracted from PersonaTaskConsumer to follow Single Responsibility Principle.
 */
@Slf4j
@Service
public class PersonaGenerationService {

    private final PersonaRepository personaRepository;
    private final AIGatewayService aiGatewayService;
    private final ObjectMapper objectMapper;

    public PersonaGenerationService(
            PersonaRepository personaRepository,
            AIGatewayService aiGatewayService,
            ObjectMapper objectMapper
    ) {
        this.personaRepository = personaRepository;
        this.aiGatewayService = aiGatewayService;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes persona generation task.
     * Generates persona details via AI and updates the Persona entity.
     *
     * Uses generationInProgress flag to prevent concurrent processing from multiple RabbitMQ consumer threads.
     * This avoids optimistic locking (version) conflicts when multiple threads try to update the same persona.
     *
     * @param task Persona generation task with persona ID, demographics, and psychographics
     * @throws ResourceNotFoundException if Persona not found
     */
    @Transactional
    public void generatePersona(PersonaGenerationTask task) {
        log.debug("Starting persona generation for persona {}", task.personaId());

        // Fetch Persona entity
        Persona persona = personaRepository.findById(task.personaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Persona not found",
                        ErrorCode.PERSONA_NOT_FOUND.getCode()
                ));

        // Check idempotency
        if (persona.getStatus() == Persona.PersonaStatus.ACTIVE) {
            log.info("Persona {} already processed, skipping", persona.getId());
            return;
        }

        // Check if another consumer thread is already processing this persona
        if (persona.getGenerationInProgress()) {
            log.warn("Persona {} is already being processed by another thread, skipping", persona.getId());
            return;
        }

        if (persona.getStatus() == Persona.PersonaStatus.FAILED) {
            log.warn("Persona {} previously failed, retrying", persona.getId());
        }

        if (persona.getStatus() != Persona.PersonaStatus.GENERATING) {
            log.warn("Unexpected persona status for {}: {}", persona.getId(), persona.getStatus());
            return;
        }

        // Set flag to prevent concurrent processing from other consumer threads
        // Uses separate transaction (REQUIRES_NEW) to ensure flag is committed immediately
        markGenerationInProgress(task.personaId());

        try {
            // Generate persona details via AI (cached by userId + demographics + psychographics)
            String personaDetailsJson = aiGatewayService.generatePersonaDetails(
                    persona.getUser().getId(),
                    task.demographicsJson(),
                    task.psychographicsJson()
            );

            // Parse and validate the JSON response
            JsonNode details = parsePersonaDetails(personaDetailsJson);
            log.info("AI persona response for persona {}: {}", persona.getId(), personaDetailsJson);
            validatePersonaDetails(details);

            // Update Persona entity with generated details
            updatePersonaEntity(persona, details);

            personaRepository.save(persona);
            log.info("Successfully generated and saved persona {}", persona.getId());
        } finally {
            // Always clear the flag, even if generation fails
            // Uses separate transaction (REQUIRES_NEW) so it commits regardless of parent transaction status
            clearGenerationInProgress(task.personaId());
        }
    }

    /**
     * Parses persona generation response from AI.
     *
     * @param personaDetailsJson JSON string from AI gateway
     * @return JsonNode with parsed details
     * @throws AIGatewayException if JSON parsing fails
     */
    private JsonNode parsePersonaDetails(String personaDetailsJson) {
        try {
            return objectMapper.readTree(personaDetailsJson);
        } catch (Exception e) {
            String message = "Failed to parse persona generation response: " + e.getMessage();
            log.error(message);
            throw new AIGatewayException(message, ErrorCode.INVALID_JSON_RESPONSE.getCode());
        }
    }

    /**
     * Validates that all required fields are present in the AI persona response JSON.
     *
     * Required fields:
     * - name: Persona full name
     * - detailed_bio: Detailed bio (150-200 words) about shopping habits, brand preferences, decision-making
     * - product_attitudes: How persona evaluates and decides on products
     *
     * Optional fields (AI may not always generate these):
     * - gender: male|female|non-binary
     * - age_group: 18-24|25-34|35-44|45-54|55-64|65+
     * - race: Asian|Caucasian|African|Hispanic|Middle Eastern|Indigenous|Mixed|Other
     *
     * @param details JsonNode with persona details from AI
     * @throws AIGatewayException if any required field is missing or null
     */
    private void validatePersonaDetails(JsonNode details) {
        String[] requiredFields = {"name", "detailed_bio", "product_attitudes"};

        for (String field : requiredFields) {
            if (!details.has(field) || details.get(field).isNull()) {
                String message = String.format(
                    "Missing or null required field in AI response: '%s'. " +
                    "Expected JSON structure: {\"name\": \"...\", \"detailed_bio\": \"...\", \"product_attitudes\": \"...\"}",
                    field
                );
                log.error("Persona validation failed: {}", message);
                throw new AIGatewayException(
                    message,
                    ErrorCode.INVALID_AI_RESPONSE.getCode()
                );
            }
        }
    }

    /**
     * Updates Persona entity with generated details from AI.
     *
     * Required field mapping:
     * - name → name
     * - detailed_bio → detailedDescription
     * - product_attitudes → productAttitudes
     *
     * @param persona Persona entity to update
     * @param details JsonNode with generated details
     */
    private void updatePersonaEntity(Persona persona, JsonNode details) {
        persona.setName(details.get("name").asText());
        persona.setDetailedDescription(details.get("detailed_bio").asText());
        persona.setProductAttitudes(details.get("product_attitudes").asText());

        persona.setStatus(Persona.PersonaStatus.ACTIVE);
    }

    /**
     * Marks persona generation as in progress in a separate transaction.
     * Uses REQUIRES_NEW to ensure the flag change is committed immediately,
     * even if the parent transaction is rolled back.
     *
     * @param personaId ID of persona to mark
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGenerationInProgress(Long personaId) {
        Persona persona = personaRepository.findById(personaId).orElse(null);
        if (persona != null) {
            persona.setGenerationInProgress(true);
            personaRepository.save(persona);
            log.debug("Marked persona {} as generation in progress", personaId);
        }
    }

    /**
     * Clears persona generation flag in a separate transaction.
     * Uses REQUIRES_NEW to ensure the flag is always cleared,
     * regardless of whether the parent transaction succeeds or rolls back.
     *
     * @param personaId ID of persona to clear flag for
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearGenerationInProgress(Long personaId) {
        try {
            Persona persona = personaRepository.findById(personaId).orElse(null);
            if (persona != null) {
                persona.setGenerationInProgress(false);
                personaRepository.save(persona);
                log.debug("Cleared generation in progress flag for persona {}", personaId);
            }
        } catch (Exception e) {
            log.error("Failed to clear generation flag for persona {}", personaId, e);
            // Don't throw - we want to ensure this doesn't break the consumer processing
        }
    }
}
