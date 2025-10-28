package ru.tigran.personafeedbackengine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

        if (persona.getStatus() == Persona.PersonaStatus.FAILED) {
            log.warn("Persona {} previously failed, retrying", persona.getId());
        }

        if (persona.getStatus() != Persona.PersonaStatus.GENERATING) {
            log.warn("Unexpected persona status for {}: {}", persona.getId(), persona.getStatus());
            return;
        }

        // Generate persona details via AI (cached by userId + demographics + psychographics)
        String personaDetailsJson = aiGatewayService.generatePersonaDetails(
                persona.getUser().getId(),
                task.demographicsJson(),
                task.psychographicsJson()
        );

        // Parse and validate the JSON response
        JsonNode details = parsePersonaDetails(personaDetailsJson);
        validatePersonaDetails(details);

        // Update Persona entity with generated details
        updatePersonaEntity(persona, details);

        personaRepository.save(persona);
        log.info("Successfully generated and saved persona {}", persona.getId());
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
     * Required fields (new format):
     * - name: Persona full name
     * - detailed_bio: Detailed bio (150-200 words) about shopping habits, brand preferences, decision-making
     * - product_attitudes: How persona evaluates and decides on products
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
     * New format maps:
     * - name → name
     * - detailed_bio → detailedDescription
     * - product_attitudes → productAttitudes
     *
     * Note: Gender, age, race fields are removed in new format.
     * These are now derived from demographics in the request.
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
}
