package ru.tigran.personafeedbackengine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tigran.personafeedbackengine.dto.AuthenticationResponse;
import ru.tigran.personafeedbackengine.dto.LoginRequest;
import ru.tigran.personafeedbackengine.dto.RegisterRequest;
import ru.tigran.personafeedbackengine.exception.ErrorCode;
import ru.tigran.personafeedbackengine.exception.ValidationException;
import ru.tigran.personafeedbackengine.model.User;
import ru.tigran.personafeedbackengine.repository.UserRepository;
import ru.tigran.personafeedbackengine.security.JwtTokenProvider;

/**
 * Service for user authentication (registration and login).
 * Handles password hashing, token generation, and user creation.
 */
@Slf4j
@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Registers a new user with email and password.
     * Password is hashed using BCrypt before storing.
     *
     * @param request Registration request with email and password
     * @return Authentication response with access token
     * @throws ValidationException if email already exists
     */
    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.email());

        // Check if email already exists
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration failed: email already exists: {}", request.email());
            throw new ValidationException(
                    ErrorCode.EMAIL_ALREADY_EXISTS.getDefaultMessage(),
                    ErrorCode.EMAIL_ALREADY_EXISTS.getCode()
            );
        }

        // Create new user with hashed password
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setIsActive(true);
        user.setDeleted(false);

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getId());

        // Generate JWT token
        String token = jwtTokenProvider.generateToken(savedUser.getId());

        return new AuthenticationResponse(savedUser.getId(), token);
    }

    /**
     * Authenticates user with email and password.
     * Returns JWT token if credentials are valid.
     *
     * @param request Login request with email and password
     * @return Authentication response with access token
     * @throws ValidationException if credentials are invalid
     */
    @Transactional(readOnly = true)
    public AuthenticationResponse login(LoginRequest request) {
        log.info("User login attempt for email: {}", request.email());

        // Find user by email
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Login failed: user not found: {}", request.email());
                    return new ValidationException(
                            ErrorCode.INVALID_CREDENTIALS.getDefaultMessage(),
                            ErrorCode.INVALID_CREDENTIALS.getCode()
                    );
                });

        // Check if user is active
        if (!user.getIsActive() || user.getDeleted()) {
            log.warn("Login failed: user is inactive or deleted: {}", user.getId());
            throw new ValidationException(
                    ErrorCode.USER_INACTIVE.getDefaultMessage(),
                    ErrorCode.USER_INACTIVE.getCode()
            );
        }

        // Verify password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed: invalid password for user: {}", user.getId());
            throw new ValidationException(
                    ErrorCode.INVALID_CREDENTIALS.getDefaultMessage(),
                    ErrorCode.INVALID_CREDENTIALS.getCode()
            );
        }

        log.info("User logged in successfully: {}", user.getId());

        // Generate JWT token
        String token = jwtTokenProvider.generateToken(user.getId());

        return new AuthenticationResponse(user.getId(), token);
    }
}
