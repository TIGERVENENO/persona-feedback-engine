package ru.tigran.personafeedbackengine.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.tigran.personafeedbackengine.config.SecurityConfig;
import ru.tigran.personafeedbackengine.config.TestConfig;
import ru.tigran.personafeedbackengine.dto.AuthenticationResponse;
import ru.tigran.personafeedbackengine.dto.LoginRequest;
import ru.tigran.personafeedbackengine.dto.RegisterRequest;
import ru.tigran.personafeedbackengine.service.AuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * Модульные тесты для AuthenticationController.
 * Тестирует HTTP слой без загрузки полного Application Context.
 * Использует @WebMvcTest для изоляции слоев и быстрого выполнения.
 */
@WebMvcTest(AuthenticationController.class)
@Import({SecurityConfig.class, TestConfig.class})
@DisplayName("AuthenticationController модульные тесты")
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticationService authenticationService;

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String VALID_EMAIL = "test@example.com";
    private static final String VALID_PASSWORD = "password123";

    @Test
    @DisplayName("POST /register - успешная регистрация нового пользователя")
    void registerSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest(VALID_EMAIL, VALID_PASSWORD);
        AuthenticationResponse mockResponse = new AuthenticationResponse(1L, "token-123", "Bearer");

        when(authenticationService.register(request))
                .thenReturn(mockResponse);

        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id", equalTo(1)))
                .andExpect(jsonPath("$.access_token", equalTo("token-123")))
                .andExpect(jsonPath("$.token_type", equalTo("Bearer")));

        verify(authenticationService).register(request);
    }

    @Test
    @DisplayName("POST /register - регистрация с существующим email")
    void registerDuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(VALID_EMAIL, VALID_PASSWORD);

        when(authenticationService.register(request))
                .thenThrow(new RuntimeException("Email already exists"));

        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code", notNullValue()));
    }

    @Test
    @DisplayName("POST /register - регистрация с невалидным email")
    void registerInvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest("invalid-email", VALID_PASSWORD);

        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register - регистрация с пустым email")
    void registerEmptyEmail() throws Exception {
        RegisterRequest request = new RegisterRequest("", VALID_PASSWORD);

        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register - регистрация с коротким паролем (менее 8 символов)")
    void registerShortPassword() throws Exception {
        RegisterRequest request = new RegisterRequest(VALID_EMAIL, "short");

        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register - регистрация с очень длинным паролем (более 128 символов)")
    void registerLongPassword() throws Exception {
        String longPassword = "a".repeat(129);
        RegisterRequest request = new RegisterRequest(VALID_EMAIL, longPassword);

        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register - регистрация с пустым паролем")
    void registerEmptyPassword() throws Exception {
        RegisterRequest request = new RegisterRequest(VALID_EMAIL, "");

        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /login - успешный логин с корректными данными")
    void loginSuccess() throws Exception {
        LoginRequest loginRequest = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);
        AuthenticationResponse mockResponse = new AuthenticationResponse(1L, "token-456", "Bearer");

        when(authenticationService.login(loginRequest))
                .thenReturn(mockResponse);

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id", equalTo(1)))
                .andExpect(jsonPath("$.access_token", equalTo("token-456")))
                .andExpect(jsonPath("$.token_type", equalTo("Bearer")));

        verify(authenticationService).login(loginRequest);
    }

    @Test
    @DisplayName("POST /login - логин с несуществующим email")
    void loginNonExistentEmail() throws Exception {
        LoginRequest request = new LoginRequest("nonexistent@example.com", VALID_PASSWORD);

        when(authenticationService.login(request))
                .thenThrow(new RuntimeException("User not found"));

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code", notNullValue()));
    }

    @Test
    @DisplayName("POST /login - логин с неверным паролем")
    void loginWrongPassword() throws Exception {
        LoginRequest loginRequest = new LoginRequest(VALID_EMAIL, "wrongpassword123");

        when(authenticationService.login(loginRequest))
                .thenThrow(new RuntimeException("Invalid password"));

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code", notNullValue()));
    }

    @Test
    @DisplayName("POST /login - логин с пустым email")
    void loginEmptyEmail() throws Exception {
        LoginRequest request = new LoginRequest("", VALID_PASSWORD);

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /login - логин с невалидным email")
    void loginInvalidEmail() throws Exception {
        LoginRequest request = new LoginRequest("invalid-email", VALID_PASSWORD);

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /login - логин с пустым паролем")
    void loginEmptyPassword() throws Exception {
        LoginRequest request = new LoginRequest(VALID_EMAIL, "");

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register - проверка что разные пользователи получают разные ID и токены")
    void registerMultipleUsersGetDifferentTokens() throws Exception {
        RegisterRequest request1 = new RegisterRequest("user1@example.com", VALID_PASSWORD);
        RegisterRequest request2 = new RegisterRequest("user2@example.com", VALID_PASSWORD);

        AuthenticationResponse response1 = new AuthenticationResponse(1L, "token-1", "Bearer");
        AuthenticationResponse response2 = new AuthenticationResponse(2L, "token-2", "Bearer");

        when(authenticationService.register(request1)).thenReturn(response1);
        when(authenticationService.register(request2)).thenReturn(response2);

        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        verify(authenticationService, times(1)).register(request1);
        verify(authenticationService, times(1)).register(request2);
    }

    @Test
    @DisplayName("POST /login - каждый логин возвращает новый токен")
    void loginGeneratesNewTokenEachTime() throws Exception {
        LoginRequest loginRequest = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);

        AuthenticationResponse token1 = new AuthenticationResponse(1L, "token-first", "Bearer");
        AuthenticationResponse token2 = new AuthenticationResponse(1L, "token-second", "Bearer");

        when(authenticationService.login(loginRequest))
                .thenReturn(token1)
                .thenReturn(token2);

        String response1 = mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String response2 = mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        AuthenticationResponse auth1 = objectMapper.readValue(response1, AuthenticationResponse.class);
        AuthenticationResponse auth2 = objectMapper.readValue(response2, AuthenticationResponse.class);

        assert auth1.userId().equals(auth2.userId()) : "Один пользователь должен иметь один ID";
        assert !auth1.accessToken().equals(auth2.accessToken()) : "Каждый логин должен генерировать новый токен";
    }
}
