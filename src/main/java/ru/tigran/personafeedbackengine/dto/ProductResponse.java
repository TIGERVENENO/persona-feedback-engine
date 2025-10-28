package ru.tigran.personafeedbackengine.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String currency,
        String category,
        List<String> keyFeatures
) {
}
