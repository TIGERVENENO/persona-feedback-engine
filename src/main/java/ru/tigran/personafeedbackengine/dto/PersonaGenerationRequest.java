package ru.tigran.personafeedbackengine.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/**
 * Request DTO for generating new AI personas using demographic and psychographic parameters.
 *
 * Triggers async persona generation workflow via RabbitMQ.
 * Will generate multiple personas (default 6) based on the provided characteristics.
 *
 * Structure:
 * - Demographics: gender, country, city, age range, activity sphere, profession, incomeLevel
 * - Psychographics: interests, additional parameters
 * - Batch generation: count field specifies how many personas to generate
 *
 * Notes:
 * - All personas are generated in English for consistency
 * - Generation is async, client must poll the persona endpoint to check status
 * - Characteristics are used for persona reusability/search via characteristicsHash
 * - count field defaults to 6 personas per request
 * - incomeLevel is an enum (LOW, MEDIUM, HIGH) for type safety
 */
public record PersonaGenerationRequest(
        @NotNull(message = "Gender cannot be null")
        Gender gender,

        @NotNull(message = "Country cannot be null")
        Country country,

        @NotBlank(message = "City cannot be blank")
        @Size(min = 1, max = 100, message = "City must be between 1 and 100 characters")
        String city,

        @NotNull(message = "Minimum age cannot be null")
        @Min(value = 18, message = "Minimum age must be at least 18")
        @Max(value = 120, message = "Minimum age must not exceed 120")
        Integer minAge,

        @NotNull(message = "Maximum age cannot be null")
        @Min(value = 18, message = "Maximum age must be at least 18")
        @Max(value = 120, message = "Maximum age must not exceed 120")
        Integer maxAge,

        @NotNull(message = "Activity sphere cannot be null")
        ActivitySphere activitySphere,

        @Size(max = 150, message = "Profession must not exceed 150 characters")
        String profession,

        @NotNull(message = "Income level cannot be null")
        IncomeLevel incomeLevel,

        @Size(max = 10, message = "Interests list must not exceed 10 items")
        List<@Size(min = 1, max = 50, message = "Each interest must be between 1 and 50 characters") String> interests,

        @Size(max = 10, message = "Additional parameters list must not exceed 10 items")
        List<@Size(min = 1, max = 100, message = "Each additional parameter must be between 1 and 100 characters") String> additionalParams,

        @Min(value = 1, message = "Count must be at least 1")
        @Max(value = 10, message = "Count must not exceed 10")
        Integer count
) {
    /**
     * Custom validation for age range
     * Ensures maxAge > minAge
     */
    public PersonaGenerationRequest {
        if (minAge != null && maxAge != null && maxAge <= minAge) {
            throw new IllegalArgumentException("Maximum age must be greater than minimum age");
        }
        // Default count to 6 if not specified
        if (count == null) {
            count = 6;
        }
    }
}

