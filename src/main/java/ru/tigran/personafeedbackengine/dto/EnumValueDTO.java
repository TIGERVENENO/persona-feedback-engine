package ru.tigran.personafeedbackengine.dto;

/**
 * DTO representing a single enum value option.
 * Used in EnumValuesResponse to provide dropdown/select options for the frontend.
 */
public record EnumValueDTO(
        String value,        // The actual enum value (e.g., "male", "RU", "IT")
        String displayName   // Human-readable display name (e.g., "Male", "Russia", "Information Technology")
) {
}
