package ru.tigran.personafeedbackengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO для создания или обновления продукта.
 *
 * Constraints:
 * - name: Обязательное поле, не может быть пустым, 1-200 символов
 * - description: Опциональное поле, до 5000 символов
 */
public record ProductRequest(
        @NotBlank(message = "Название продукта не может быть пустым")
        @Size(min = 1, max = 200, message = "Название должно быть от 1 до 200 символов")
        String name,

        @Size(max = 5000, message = "Описание не должно превышать 5000 символов")
        String description
) {
}
