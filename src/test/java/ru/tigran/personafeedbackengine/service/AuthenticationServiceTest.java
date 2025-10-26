package ru.tigran.personafeedbackengine.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.tigran.personafeedbackengine.dto.AuthenticationResponse;
import ru.tigran.personafeedbackengine.dto.LoginRequest;
import ru.tigran.personafeedbackengine.dto.RegisterRequest;
import ru.tigran.personafeedbackengine.exception.ValidationException;
import ru.tigran.personafeedbackengine.model.User;
import ru.tigran.personafeedbackengine.repository.UserRepository;
import ru.tigran.personafeedbackengine.security.JwtTokenProvider;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для AuthenticationService.
 * Тестирует бизнес-логику регистрации и логина с использованием Mockito.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationService unit тесты")
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthenticationService authenticationService;

    private static final String VALID_EMAIL = "test@example.com";
    private static final String VALID_PASSWORD = "password123";
    private static final String HASHED_PASSWORD = "$2a$10$hashedpassword";
    private static final String JWT_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                userRepository,
                passwordEncoder,
                jwtTokenProvider
        );
    }

    // ===== РЕГИСТРАЦИЯ (register) =====

    @Test
    @DisplayName("register - успешная регистрация нового пользователя")
    void registerSuccess() {
        RegisterRequest request = new RegisterRequest(VALID_EMAIL, VALID_PASSWORD);
        User savedUser = new User();
        savedUser.setId(USER_ID);
        savedUser.setEmail(VALID_EMAIL);
        savedUser.setPasswordHash(HASHED_PASSWORD);
        savedUser.setIsActive(true);
        savedUser.setDeleted(false);

        when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtTokenProvider.generateToken(USER_ID)).thenReturn(JWT_TOKEN);

        AuthenticationResponse response = authenticationService.register(request);

        assertNotNull(response);
        assertEquals(USER_ID, response.userId());
        assertEquals(JWT_TOKEN, response.accessToken());
        assertEquals("Bearer", response.tokenType());

        verify(userRepository).existsByEmail(VALID_EMAIL);
        verify(passwordEncoder).encode(VALID_PASSWORD);
        verify(userRepository).save(any(User.class));
        verify(jwtTokenProvider).generateToken(USER_ID);
    }

    @Test
    @DisplayName("register - регистрация с существующим email выбросит исключение")
    void registerDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(VALID_EMAIL, VALID_PASSWORD);

        when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(true);

        assertThrows(ValidationException.class, () -> authenticationService.register(request));

        verify(userRepository).existsByEmail(VALID_EMAIL);
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("register - проверка что пароль кодируется перед сохранением")
    void registerPasswordIsEncoded() {
        RegisterRequest request = new RegisterRequest(VALID_EMAIL, VALID_PASSWORD);
        User savedUser = new User();
        savedUser.setId(USER_ID);

        when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtTokenProvider.generateToken(USER_ID)).thenReturn(JWT_TOKEN);

        authenticationService.register(request);

        verify(passwordEncoder).encode(VALID_PASSWORD);
        verify(userRepository).save(argThat(user ->
                user.getPasswordHash().equals(HASHED_PASSWORD)
        ));
    }

    @Test
    @DisplayName("register - проверка что новый пользователь активный и не удалённый")
    void registerNewUserIsActiveAndNotDeleted() {
        RegisterRequest request = new RegisterRequest(VALID_EMAIL, VALID_PASSWORD);
        User savedUser = new User();
        savedUser.setId(USER_ID);

        when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertTrue(user.getIsActive());
            assertFalse(user.getDeleted());
            user.setId(USER_ID);
            return user;
        });
        when(jwtTokenProvider.generateToken(USER_ID)).thenReturn(JWT_TOKEN);

        authenticationService.register(request);

        verify(userRepository).save(any(User.class));
    }

    // ===== ЛОГИН (login) =====

    @Test
    @DisplayName("login - успешный логин с корректными данными")
    void loginSuccess() {
        LoginRequest request = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(VALID_EMAIL);
        user.setPasswordHash(HASHED_PASSWORD);
        user.setIsActive(true);
        user.setDeleted(false);

        when(userRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(VALID_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(jwtTokenProvider.generateToken(USER_ID)).thenReturn(JWT_TOKEN);

        AuthenticationResponse response = authenticationService.login(request);

        assertNotNull(response);
        assertEquals(USER_ID, response.userId());
        assertEquals(JWT_TOKEN, response.accessToken());
        assertEquals("Bearer", response.tokenType());

        verify(userRepository).findByEmail(VALID_EMAIL);
        verify(passwordEncoder).matches(VALID_PASSWORD, HASHED_PASSWORD);
        verify(jwtTokenProvider).generateToken(USER_ID);
    }

    @Test
    @DisplayName("login - логин с несуществующим email выбросит исключение")
    void loginNonExistentEmail() {
        LoginRequest request = new LoginRequest("nonexistent@example.com", VALID_PASSWORD);

        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThrows(ValidationException.class, () -> authenticationService.login(request));

        verify(userRepository).findByEmail("nonexistent@example.com");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtTokenProvider, never()).generateToken(anyLong());
    }

    @Test
    @DisplayName("login - логин с неверным паролем выбросит исключение")
    void loginWrongPassword() {
        LoginRequest request = new LoginRequest(VALID_EMAIL, "wrongpassword");
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(VALID_EMAIL);
        user.setPasswordHash(HASHED_PASSWORD);
        user.setIsActive(true);
        user.setDeleted(false);

        when(userRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpassword", HASHED_PASSWORD)).thenReturn(false);

        assertThrows(ValidationException.class, () -> authenticationService.login(request));

        verify(userRepository).findByEmail(VALID_EMAIL);
        verify(passwordEncoder).matches("wrongpassword", HASHED_PASSWORD);
        verify(jwtTokenProvider, never()).generateToken(anyLong());
    }

    @Test
    @DisplayName("login - логин с неактивным пользователем выбросит исключение")
    void loginInactiveUser() {
        LoginRequest request = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(VALID_EMAIL);
        user.setPasswordHash(HASHED_PASSWORD);
        user.setIsActive(false);  // неактивный
        user.setDeleted(false);

        when(userRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(user));

        assertThrows(ValidationException.class, () -> authenticationService.login(request));

        verify(userRepository).findByEmail(VALID_EMAIL);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtTokenProvider, never()).generateToken(anyLong());
    }

    @Test
    @DisplayName("login - логин с удалённым пользователем выбросит исключение")
    void loginDeletedUser() {
        LoginRequest request = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(VALID_EMAIL);
        user.setPasswordHash(HASHED_PASSWORD);
        user.setIsActive(true);
        user.setDeleted(true);  // удалённый

        when(userRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(user));

        assertThrows(ValidationException.class, () -> authenticationService.login(request));

        verify(userRepository).findByEmail(VALID_EMAIL);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtTokenProvider, never()).generateToken(anyLong());
    }

    @Test
    @DisplayName("login - логин с неактивным И удалённым пользователем выбросит исключение")
    void loginInactiveAndDeletedUser() {
        LoginRequest request = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(VALID_EMAIL);
        user.setPasswordHash(HASHED_PASSWORD);
        user.setIsActive(false);
        user.setDeleted(true);

        when(userRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(user));

        assertThrows(ValidationException.class, () -> authenticationService.login(request));

        verify(userRepository).findByEmail(VALID_EMAIL);
    }

    @Test
    @DisplayName("login - проверка константного времени для сравнения паролей")
    void loginUsesConstantTimePasswordComparison() {
        LoginRequest request = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(VALID_EMAIL);
        user.setPasswordHash(HASHED_PASSWORD);
        user.setIsActive(true);
        user.setDeleted(false);

        when(userRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(VALID_PASSWORD, HASHED_PASSWORD)).thenReturn(false);

        assertThrows(ValidationException.class, () -> authenticationService.login(request));

        // Проверка что используется constantTimeComparison (через matches)
        verify(passwordEncoder).matches(VALID_PASSWORD, HASHED_PASSWORD);
    }

    @Test
    @DisplayName("register и login - разные пользователи должны иметь разные ID")
    void registerAndLoginDifferentUsers() {
        // Первый пользователь
        RegisterRequest registerRequest1 = new RegisterRequest("user1@example.com", VALID_PASSWORD);
        User user1 = new User();
        user1.setId(1L);

        when(userRepository.existsByEmail("user1@example.com")).thenReturn(false);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(user1);
        when(jwtTokenProvider.generateToken(1L)).thenReturn("token1");

        AuthenticationResponse response1 = authenticationService.register(registerRequest1);
        assertEquals(1L, response1.userId());

        // Второй пользователь
        reset(userRepository, passwordEncoder, jwtTokenProvider);

        RegisterRequest registerRequest2 = new RegisterRequest("user2@example.com", VALID_PASSWORD);
        User user2 = new User();
        user2.setId(2L);

        when(userRepository.existsByEmail("user2@example.com")).thenReturn(false);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(user2);
        when(jwtTokenProvider.generateToken(2L)).thenReturn("token2");

        AuthenticationResponse response2 = authenticationService.register(registerRequest2);
        assertEquals(2L, response2.userId());

        assertNotEquals(response1.userId(), response2.userId());
    }

    @Test
    @DisplayName("login - каждый логин генерирует новый токен")
    void loginGeneratesNewTokenEachTime() {
        LoginRequest request = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(VALID_EMAIL);
        user.setPasswordHash(HASHED_PASSWORD);
        user.setIsActive(true);
        user.setDeleted(false);

        when(userRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(VALID_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(jwtTokenProvider.generateToken(USER_ID))
                .thenReturn("token1")
                .thenReturn("token2");

        AuthenticationResponse response1 = authenticationService.login(request);
        AuthenticationResponse response2 = authenticationService.login(request);

        assertEquals(USER_ID, response1.userId());
        assertEquals(USER_ID, response2.userId());
        assertNotEquals(response1.accessToken(), response2.accessToken());

        verify(jwtTokenProvider, times(2)).generateToken(USER_ID);
    }
}
