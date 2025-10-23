package ru.tigran.personafeedbackengine.dto;

public record PersonaResponse(
        Long id,
        String name,
        String detailedDescription,
        String gender,
        String ageGroup,
        String race,
        String avatarUrl,
        String status
) {
}
