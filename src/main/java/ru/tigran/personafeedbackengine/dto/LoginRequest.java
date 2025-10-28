package ru.tigran.personafeedbackengine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for user login.
 */
public record LoginRequest(
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email should be valid")
    @Schema(defaultValue = "testuser@example.com")
    String email,

    @NotBlank(message = "Password cannot be blank")
    @Schema(defaultValue = "TestPass123")
    String password
) {
}
