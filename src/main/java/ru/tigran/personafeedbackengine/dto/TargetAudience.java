package ru.tigran.personafeedbackengine.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Target audience parameters for bulk persona generation.
 *
 * Structure:
 * - gender: List of genders (["male", "female"])
 * - ageRanges: List of age ranges (["18-25", "26-35"])
 * - regions: List of regions (["moscow", "regions"])
 * - incomes: List of income levels (["medium", "high"])
 *
 * Example:
 * {
 *   "genders": ["male", "female"],
 *   "ageRanges": ["25-35"],
 *   "regions": ["moscow"],
 *   "incomes": ["medium"]
 * }
 */
public record TargetAudience(
        @NotEmpty(message = "At least one gender must be specified")
        @Size(min = 1, max = 3, message = "Gender list must have 1-3 elements")
        List<String> genders,  // ["male", "female", "other"]

        @NotEmpty(message = "At least one age range must be specified")
        @Size(min = 1, max = 4, message = "Age ranges list must have 1-4 elements")
        List<String> ageRanges,  // ["18-25", "26-35", "36-45", "46+"]

        @NotEmpty(message = "At least one region must be specified")
        @Size(min = 1, max = 3, message = "Regions list must have 1-3 elements")
        List<String> regions,  // ["moscow", "spb", "regions"]

        @NotEmpty(message = "At least one income level must be specified")
        @Size(min = 1, max = 3, message = "Income levels list must have 1-3 elements")
        List<String> incomes  // ["low", "medium", "high"]
) {
}
