package ru.tigran.personafeedbackengine.dto;

/**
 * Демографические данные для генерации персоны.
 */
public record PersonaDemographics(
        String age,
        String gender,
        String location,
        String occupation,
        String income
) {
}
