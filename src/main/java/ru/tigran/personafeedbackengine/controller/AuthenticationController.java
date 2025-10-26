package ru.tigran.personafeedbackengine.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.tigran.personafeedbackengine.dto.AuthenticationResponse;
import ru.tigran.personafeedbackengine.dto.LoginRequest;
import ru.tigran.personafeedbackengine.dto.RegisterRequest;
import ru.tigran.personafeedbackengine.service.AuthenticationService;

/**
 * REST API for user authentication (registration and login).
 * Endpoints for user registration with email/password and login.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * Register a new user with email and password.
     *
     * @param request Registration request with email and password
     * @return 201 Created with user ID and JWT access token
     */
    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        log.info("POST /api/v1/auth/register - email: {}", request.email());

        AuthenticationResponse response = authenticationService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Login with email and password.
     *
     * @param request Login request with email and password
     * @return 200 OK with user ID and JWT access token
     */
    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        log.info("POST /api/v1/auth/login - email: {}", request.email());

        AuthenticationResponse response = authenticationService.login(request);

        return ResponseEntity.ok(response);
    }
}
