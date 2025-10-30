package ru.tigran.personafeedbackengine.dto;

import java.util.List;

/**
 * Response DTO containing all available enum values for persona creation.
 * Used by GET /api/v1/personas/enums endpoint to populate frontend dropdowns.
 */
public record EnumValuesResponse(
        List<EnumValueDTO> genders,         // Available gender options
        List<EnumValueDTO> countries,       // Available country options
        List<EnumValueDTO> activitySpheres  // Available activity sphere options
) {
}
