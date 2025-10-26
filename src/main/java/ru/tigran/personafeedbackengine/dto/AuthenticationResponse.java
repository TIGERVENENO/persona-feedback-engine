package ru.tigran.personafeedbackengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for authentication (registration/login).
 */
public record AuthenticationResponse(
    @JsonProperty("user_id")
    Long userId,

    @JsonProperty("access_token")
    String accessToken,

    @JsonProperty("token_type")
    String tokenType
) {
    public AuthenticationResponse(Long userId, String accessToken) {
        this(userId, accessToken, "Bearer");
    }
}
