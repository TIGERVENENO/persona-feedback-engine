package ru.tigran.personafeedbackengine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import ru.tigran.personafeedbackengine.config.TestConfig;
import ru.tigran.personafeedbackengine.dto.*;
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
@AutoConfigureMockMvc(addFilters = false)
@Import({TestConfig.class})
@DisplayName("PersonaController модульные тесты")
class PersonaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Mock
    private PersonaService personaService;

    private static final String PERSONA_URL = "/api/v1/personas";
    private static final String VALID_EMAIL = "test@example.com";
    private static final String VALID_PASSWORD = "password123";
    private static final Long VALID_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        // Подготовить SecurityContext с аутентификацией для каждого теста
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(VALID_USER_ID, null));
        SecurityContextHolder.setContext(context);
    }

    private PersonaGenerationRequest createValidRequest() {
        return new PersonaGenerationRequest(
                Gender.MALE,                           // gender
                Country.US,                            // country
                "New York",                            // city
                30,                                    // minAge
                40,                                    // maxAge
                ActivitySphere.CONSULTING,             // activitySphere
                "Product Manager",                     // profession
                IncomeLevel.HIGH,                      // incomeLevel
                java.util.List.of("Innovation", "Leadership", "Technology"),  // interests
                java.util.List.of("Active", "tech-savvy lifestyle"),  // additionalParams
                6                                      // count
        );
    }

    @Test
    @DisplayName("POST /personas - успешное создание персоны с валидными демографическими данными и JWT токеном")
    void generatePersonaSuccess() throws Exception {
        PersonaGenerationRequest request = createValidRequest();

        when(personaService.startPersonaGeneration(VALID_USER_ID, request)).thenReturn(100L);

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo("GENERATING")));
    }

    @Test
    @DisplayName("POST /personas - успешно с валидными демографическими данными")
    void generatePersonaValidDemographics() throws Exception {
        PersonaGenerationRequest request = createValidRequest();

        when(personaService.startPersonaGeneration(VALID_USER_ID, request)).thenReturn(101L);

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo("GENERATING")));
    }

    @Test
    @DisplayName("POST /personas - успешно с полными психографическими данными")
    void generatePersonaWithPsychographics() throws Exception {
        PersonaGenerationRequest request = createValidRequest();

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
        PersonaGenerationRequest request = createValidRequest();

        when(personaService.startPersonaGeneration(VALID_USER_ID, request)).thenReturn(103L);

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("POST /personas - возвращает корректный ID в ответе")
    void generatePersonaReturnsValidId() throws Exception {
        PersonaGenerationRequest request = createValidRequest();

        when(personaService.startPersonaGeneration(VALID_USER_ID, request)).thenReturn(104L);

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId", greaterThan(0)));
    }

    @Test
    @DisplayName("POST /personas - 400 Bad Request при null demographics")
    void generatePersonaNullDemographics() throws Exception {
        String invalidJson = "{\"psychographics\": {\"values\": \"test\", \"lifestyle\": \"test\", \"painPoints\": \"test\"}}";

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /personas - 400 Bad Request при null psychographics")
    void generatePersonaNullPsychographics() throws Exception {
        String invalidJson = "{\"demographics\": {\"age\": \"30\", \"gender\": \"Male\", \"location\": \"NY\", \"occupation\": \"PM\", \"income\": \"100k\"}}";

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /personas - 403 Forbidden без JWT токена")
    void generatePersonaWithoutToken() throws Exception {
        // Очистить SecurityContext для этого теста
        SecurityContextHolder.clearContext();

        PersonaGenerationRequest request = createValidRequest();

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /personas - 400 Bad Request при пустом JSON")
    void generatePersonaMissingBothFields() throws Exception {
        String invalidJson = "{}";

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /personas - множественные запросы создают разные персоны")
    void generatePersonaMultipleRequestsCreateDifferentPersonas() throws Exception {
        PersonaGenerationRequest request = createValidRequest();

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
        PersonaGenerationRequest request = createValidRequest();

        when(personaService.startPersonaGeneration(VALID_USER_ID, request)).thenReturn(112L);

        mockMvc.perform(post(PERSONA_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", equalTo("GENERATING")));
    }
}
