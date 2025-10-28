package ru.tigran.personafeedbackengine.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request DTO для создания или обновления продукта.
 *
 * Constraints:
 * - name: Обязательное поле, не может быть пустым, 1-200 символов
 * - description: Опциональное поле, до 5000 символов
 * - price: Опциональное, минимум 0.00
 * - category: Опциональное, до 100 символов
 * - keyFeatures: Опциональный список ключевых характеристик
 */
public record ProductRequest(
        @NotBlank(message = "Название продукта не может быть пустым")
        @Size(min = 1, max = 200, message = "Название должно быть от 1 до 200 символов")
        String name,

        @Size(max = 5000, message = "Описание не должно превышать 5000 символов")
        String description,

        @DecimalMin(value = "0.00", message = "Цена не может быть отрицательной")
        BigDecimal price,

        @Size(max = 100, message = "Категория не должна превышать 100 символов")
        String category,

        @Size(max = 50, message = "Максимум 50 характеристик")
        List<@NotBlank(message = "Характеристика не может быть пустой")
        @Size(max = 200, message = "Характеристика не должна превышать 200 символов")
                String> keyFeatures
) {
}
