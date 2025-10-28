package ru.tigran.personafeedbackengine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for user registration.
 */
public record RegisterRequest(
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email should be valid")
    @Schema(defaultValue = "testuser@example.com")
    String email,

    @NotBlank(message = "Password cannot be blank")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @Schema(defaultValue = "TestPass123")
    String password
) {
}
