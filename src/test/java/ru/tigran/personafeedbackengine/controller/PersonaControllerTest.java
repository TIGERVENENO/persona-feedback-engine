package ru.tigran.personafeedbackengine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.tigran.personafeedbackengine.dto.LoginRequest;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationRequest;
import ru.tigran.personafeedbackengine.dto.RegisterRequest;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты для PersonaController.
 * Тестирует создание персон с JWT аутентификацией.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("PersonaController интеграционные тесты")
class PersonaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String authToken;
    private Long userId;
    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String PERSONA_URL = "/api/v1/personas";
    private static final String VALID_EMAIL = "test@example.com";
    private static final String VALID_PASSWORD = "password123";
    private static final String VALID_PROMPT = "A technical product manager focused on DevOps tools";

    @BeforeEach
    void setUp() throws Exception {
        // Регистрируем тестового пользователя
        RegisterRequest registerRequest = new RegisterRequest(VALID_EMAIL, VALID_PASSWORD);
        String registerResponse = mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Извлекаем токен и ID пользователя
        String[] parts = registerResponse.split("\"");
        userId = Long.parseLong(parts[3]);

        // Ищем access_token
        int tokenIndex = registerResponse.indexOf("\"access_token\":\"");
        authToken = registerResponse.substring(tokenIndex + 17, registerResponse.indexOf("\"", tokenIndex + 17));
    }

    // ===== УСПЕШНЫЕ СЦЕНАРИИ =====

    @Test
    @DisplayName("POST /personas - успешное создание персоны с валидным промптом и JWT токеном")
    void generatePersonaSuccess() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);

        mockMvc.perform(post(PERSONA_URL)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo("GENERATING")));
    }

    @Test
    @DisplayName("POST /personas - успешно с минимальным промптом (1 символ)")
    void generatePersonaMinimumPrompt() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest("A");

        mockMvc.perform(post(PERSONA_URL)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo("GENERATING")));
    }

    @Test
    @DisplayName("POST /personas - успешно с максимальным промптом (2000 символов)")
    void generatePersonaMaximumPrompt() throws Exception {
        String maxPrompt = "a".repeat(2000);
        PersonaGenerationRequest request = new PersonaGenerationRequest(maxPrompt);

        mockMvc.perform(post(PERSONA_URL)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo("GENERATING")));
    }

    @Test
    @DisplayName("POST /personas - возврат HTTP 202 Accepted")
    void generatePersonaReturnsAccepted() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);

        mockMvc.perform(post(PERSONA_URL)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("POST /personas - возвращает корректный ID в ответе")
    void generatePersonaReturnsValidId() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);

        mockMvc.perform(post(PERSONA_URL)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id", isA(Number.class)))
                .andExpect(jsonPath("$.id", greaterThan(0)));
    }

    // ===== ОШИБОЧНЫЕ СЦЕНАРИИ =====

    @Test
    @DisplayName("POST /personas - 400 Bad Request при промпте длиной > 2000 символов")
    void generatePersonaPromptTooLong() throws Exception {
        String longPrompt = "a".repeat(2001);
        PersonaGenerationRequest request = new PersonaGenerationRequest(longPrompt);

        mockMvc.perform(post(PERSONA_URL)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /personas - 400 Bad Request при пустом промпте")
    void generatePersonaEmptyPrompt() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest("");

        mockMvc.perform(post(PERSONA_URL)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /personas - 401 Unauthorized без JWT токена")
    void generatePersonaWithoutToken() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /personas - 401 Unauthorized с невалидным JWT токеном")
    void generatePersonaWithInvalidToken() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);

        mockMvc.perform(post(PERSONA_URL)
                .header("Authorization", "Bearer invalid.token.here")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /personas - 400 Bad Request без промпта в теле запроса")
    void generatePersonaMissingPrompt() throws Exception {
        String invalidJson = "{}";

        mockMvc.perform(post(PERSONA_URL)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /personas - множественные запросы создают разные персоны")
    void generatePersonaMultipleRequestsCreateDifferentPersonas() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);

        String response1 = mockMvc.perform(post(PERSONA_URL)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String response2 = mockMvc.perform(post(PERSONA_URL)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Извлекаем ID из обоих ответов
        Long id1 = Long.parseLong(response1.split("\"id\":")[1].split(",")[0]);
        Long id2 = Long.parseLong(response2.split("\"id\":")[1].split(",")[0]);

        assert !id1.equals(id2) : "Разные запросы должны создавать разные персоны";
    }

    @Test
    @DisplayName("POST /personas - статус всегда GENERATING при успешном создании")
    void generatePersonaStatusAlwaysGenerating() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);

        mockMvc.perform(post(PERSONA_URL)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", equalTo("GENERATING")));
    }

    @Test
    @DisplayName("POST /personas - разные пользователи создают персоны независимо друг от друга")
    void generatePersonaDifferentUsersIndependent() throws Exception {
        // Создаём второго пользователя
        RegisterRequest registerRequest2 = new RegisterRequest("test2@example.com", VALID_PASSWORD);
        String registerResponse2 = mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest2)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String[] parts = registerResponse2.split("\"");
        Long userId2 = Long.parseLong(parts[3]);

        int tokenIndex = registerResponse2.indexOf("\"access_token\":\"");
        String authToken2 = registerResponse2.substring(tokenIndex + 17, registerResponse2.indexOf("\"", tokenIndex + 17));

        // Создаём персону для первого пользователя
        PersonaGenerationRequest request1 = new PersonaGenerationRequest("First user prompt");
        String response1 = mockMvc.perform(post(PERSONA_URL)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Создаём персону для второго пользователя
        PersonaGenerationRequest request2 = new PersonaGenerationRequest("Second user prompt");
        String response2 = mockMvc.perform(post(PERSONA_URL)
                .header("Authorization", "Bearer " + authToken2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Оба должны иметь разные ID
        Long id1 = Long.parseLong(response1.split("\"id\":")[1].split(",")[0]);
        Long id2 = Long.parseLong(response2.split("\"id\":")[1].split(",")[0]);

        assert !id1.equals(id2) : "Разные пользователи должны иметь разные ID персон";
        assert !userId.equals(userId2) : "Разные пользователи должны иметь разные ID";
    }

    @Test
    @DisplayName("POST /personas - 401 Unauthorized с неправильным Authorization header")
    void generatePersonaWrongAuthorizationHeaderFormat() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);

        mockMvc.perform(post(PERSONA_URL)
                .header("Authorization", "InvalidFormat " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
