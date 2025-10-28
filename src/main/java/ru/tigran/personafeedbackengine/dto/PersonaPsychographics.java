package ru.tigran.personafeedbackengine.dto;

/**
 * Психографические данные для генерации персоны.
 */
public record PersonaPsychographics(
        String values,
        String lifestyle,
        String painPoints
) {
}
