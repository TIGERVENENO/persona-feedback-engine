package ru.tigran.personafeedbackengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for authentication (registration/login).
 */
@Schema(description = "Authentication response containing JWT token and user info")
public record AuthenticationResponse(
    @Schema(description = "User ID of the authenticated user", example = "123")
    @JsonProperty("user_id")
    Long userId,

    @Schema(description = "JWT access token for API authentication",
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    @JsonProperty("access_token")
    String accessToken,

    @Schema(description = "Token type, always 'Bearer'", example = "Bearer")
    @JsonProperty("token_type")
    String tokenType
) {
    public AuthenticationResponse(Long userId, String accessToken) {
        this(userId, accessToken, "Bearer");
    }
}
