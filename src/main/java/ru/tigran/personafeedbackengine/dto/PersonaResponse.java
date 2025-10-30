package ru.tigran.personafeedbackengine.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for persona data.
 * Contains complete persona information including demographics, psychographics, and AI-generated details.
 */
public record PersonaResponse(
        // Identifier and status
        Long id,
        String status,  // GENERATING, ACTIVE, FAILED

        // AI-generated details
        String name,
        String detailedDescription,  // 150-200 word bio about shopping habits, preferences, decision-making
        String productAttitudes,     // How this persona evaluates and decides on products

        // Demographics (from request)
        String gender,               // Male, Female, Non-binary
        String country,              // ISO 3166-1 alpha-2 code
        String city,
        Integer minAge,
        Integer maxAge,
        String activitySphere,       // IT, Finance, Healthcare, etc.
        String profession,           // Optional, specific role within activity sphere
        String income,               // Optional, e.g. "$50k-$75k"

        // Psychographics (from request)
        List<String> interests,      // Optional array of interests/hobbies
        String additionalParams,     // Optional additional characteristics

        // UI
        String avatarUrl,

        // Metadata
        LocalDateTime createdAt
) {
}

