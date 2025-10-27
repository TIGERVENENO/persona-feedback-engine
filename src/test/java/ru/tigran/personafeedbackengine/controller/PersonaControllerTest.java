package ru.tigran.personafeedbackengine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.tigran.personafeedbackengine.config.TestConfig;
import ru.tigran.personafeedbackengine.config.TestSecurityConfig;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationRequest;
import ru.tigran.personafeedbackengine.service.PersonaService;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Модульные тесты для PersonaController.
 * Тестирует создание персон с JWT аутентификацией.
 * Использует @WebMvcTest для изоляции слоев.
 */
@WebMvcTest(PersonaController.class)
@Import({TestSecurityConfig.class, TestConfig.class})
@DisplayName("PersonaController модульные тесты")
class PersonaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PersonaService personaService;

    private static final String PERSONA_URL = "/api/v1/personas";
    private static final String VALID_EMAIL = "test@example.com";
    private static final String VALID_PASSWORD = "password123";
    private static final String VALID_PROMPT = "A technical product manager focused on DevOps tools";
    private static final Long VALID_USER_ID = 1L;

    @Test
    @DisplayName("POST /personas - успешное создание персоны с валидным промптом и JWT токеном")
    void generatePersonaSuccess() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);

        when(personaService.startPersonaGeneration(VALID_USER_ID, request)).thenReturn(100L);

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo("GENERATING")));
    }

    @Test
    @DisplayName("POST /personas - успешно с минимальным промптом (1 символ)")
    void generatePersonaMinimumPrompt() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest("A");

        when(personaService.startPersonaGeneration(VALID_USER_ID, request)).thenReturn(101L);

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo("GENERATING")));
    }

    @Test
    @DisplayName("POST /personas - успешно с максимальным промптом (2000 символов)")
    void generatePersonaMaximumPrompt() throws Exception {
        String maxPrompt = "a".repeat(2000);
        PersonaGenerationRequest request = new PersonaGenerationRequest(maxPrompt);

        when(personaService.startPersonaGeneration(VALID_USER_ID, request)).thenReturn(102L);

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo("GENERATING")));
    }

    @Test
    @DisplayName("POST /personas - возврат HTTP 202 Accepted")
    void generatePersonaReturnsAccepted() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);

        when(personaService.startPersonaGeneration(VALID_USER_ID, request)).thenReturn(103L);

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("POST /personas - возвращает корректный ID в ответе")
    void generatePersonaReturnsValidId() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);

        when(personaService.startPersonaGeneration(VALID_USER_ID, request)).thenReturn(104L);

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId", greaterThan(0)));
    }

    @Test
    @DisplayName("POST /personas - 400 Bad Request при промпте длиной > 2000 символов")
    void generatePersonaPromptTooLong() throws Exception {
        String longPrompt = "a".repeat(2001);
        PersonaGenerationRequest request = new PersonaGenerationRequest(longPrompt);

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /personas - 400 Bad Request при пустом промпте")
    void generatePersonaEmptyPrompt() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest("");

        mockMvc.perform(post(PERSONA_URL)
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
    @DisplayName("POST /personas - 400 Bad Request без промпта в теле запроса")
    void generatePersonaMissingPrompt() throws Exception {
        String invalidJson = "{}";

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /personas - множественные запросы создают разные персоны")
    void generatePersonaMultipleRequestsCreateDifferentPersonas() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);

        when(personaService.startPersonaGeneration(VALID_USER_ID, request))
                .thenReturn(110L)
                .thenReturn(111L);

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        verify(personaService, times(2)).startPersonaGeneration(VALID_USER_ID, request);
    }

    @Test
    @DisplayName("POST /personas - статус всегда GENERATING при успешном создании")
    void generatePersonaStatusAlwaysGenerating() throws Exception {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);

        when(personaService.startPersonaGeneration(VALID_USER_ID, request)).thenReturn(112L);

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", equalTo("GENERATING")));
    }
}
