package ru.tigran.personafeedbackengine.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationRequest;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationTask;
import ru.tigran.personafeedbackengine.exception.ValidationException;
import ru.tigran.personafeedbackengine.model.Persona;
import ru.tigran.personafeedbackengine.model.User;
import ru.tigran.personafeedbackengine.repository.PersonaRepository;
import ru.tigran.personafeedbackengine.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для PersonaService.
 * Тестирует создание персон и отправку задач в очередь.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PersonaService unit тесты")
class PersonaServiceTest {

    @Mock
    private PersonaRepository personaRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private MeterRegistry meterRegistry;
    private PersonaService personaService;

    private static final Long USER_ID = 1L;
    private static final String VALID_PROMPT = "A technical product manager focused on DevOps tools";
    private static final String LONG_PROMPT = "a".repeat(2001);
    private static final int MAX_PROMPT_LENGTH = 2000;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        personaService = new PersonaService(
                personaRepository,
                userRepository,
                rabbitTemplate,
                MAX_PROMPT_LENGTH,
                meterRegistry
        );
    }

    // ===== УСПЕШНЫЕ СЦЕНАРИИ =====

    @Test
    @DisplayName("startPersonaGeneration - успешное создание персоны с валидным промптом")
    void startPersonaGenerationSuccess() {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);
        User user = new User();
        user.setId(USER_ID);

        Persona savedPersona = new Persona();
        savedPersona.setId(42L);
        savedPersona.setUser(user);
        savedPersona.setStatus(Persona.PersonaStatus.GENERATING);
        savedPersona.setGenerationPrompt(VALID_PROMPT);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(personaRepository.save(any(Persona.class))).thenReturn(savedPersona);

        Long personaId = personaService.startPersonaGeneration(USER_ID, request);

        assertEquals(42L, personaId);
        verify(personaRepository).save(argThat(persona ->
                persona.getStatus() == Persona.PersonaStatus.GENERATING &&
                        persona.getGenerationPrompt().equals(VALID_PROMPT)
        ));
        verify(rabbitTemplate).convertAndSend(
                anyString(),
                eq("persona.generation"),
                any(PersonaGenerationTask.class)
        );
    }

    @Test
    @DisplayName("startPersonaGeneration - проверка отправки задачи в очередь с корректными параметрами")
    void startPersonaGenerationPublishesTaskToQueue() {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);
        User user = new User();
        user.setId(USER_ID);

        Persona savedPersona = new Persona();
        savedPersona.setId(42L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(personaRepository.save(any(Persona.class))).thenReturn(savedPersona);

        personaService.startPersonaGeneration(USER_ID, request);

        ArgumentCaptor<PersonaGenerationTask> taskCaptor = ArgumentCaptor.forClass(PersonaGenerationTask.class);
        verify(rabbitTemplate).convertAndSend(
                anyString(),
                eq("persona.generation"),
                taskCaptor.capture()
        );

        PersonaGenerationTask capturedTask = taskCaptor.getValue();
        assertEquals(42L, capturedTask.personaId());
        assertEquals(VALID_PROMPT, capturedTask.userPrompt());
    }

    @Test
    @DisplayName("startPersonaGeneration - успешно с минимальным промптом (1 символ)")
    void startPersonaGenerationMinimumPrompt() {
        PersonaGenerationRequest request = new PersonaGenerationRequest("A");
        User user = new User();
        user.setId(USER_ID);

        Persona savedPersona = new Persona();
        savedPersona.setId(42L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(personaRepository.save(any(Persona.class))).thenReturn(savedPersona);

        Long personaId = personaService.startPersonaGeneration(USER_ID, request);

        assertEquals(42L, personaId);
        verify(personaRepository).save(any(Persona.class));
        verify(rabbitTemplate).convertAndSend(
                anyString(),
                eq("persona.generation"),
                any(PersonaGenerationTask.class)
        );
    }

    @Test
    @DisplayName("startPersonaGeneration - успешно с максимальным промптом (2000 символов)")
    void startPersonaGenerationMaximumPrompt() {
        String maxPrompt = "a".repeat(2000);
        PersonaGenerationRequest request = new PersonaGenerationRequest(maxPrompt);
        User user = new User();
        user.setId(USER_ID);

        Persona savedPersona = new Persona();
        savedPersona.setId(42L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(personaRepository.save(any(Persona.class))).thenReturn(savedPersona);

        Long personaId = personaService.startPersonaGeneration(USER_ID, request);

        assertEquals(42L, personaId);
        verify(personaRepository).save(any(Persona.class));
    }

    // ===== ОШИБОЧНЫЕ СЦЕНАРИИ =====

    @Test
    @DisplayName("startPersonaGeneration - выброс исключения при промпте длиной > 2000 символов")
    void startPersonaGenerationPromptTooLong() {
        PersonaGenerationRequest request = new PersonaGenerationRequest(LONG_PROMPT);

        assertThrows(RuntimeException.class, () ->
                personaService.startPersonaGeneration(USER_ID, request)
        );

        verify(userRepository, never()).findById(anyLong());
        verify(personaRepository, never()).save(any(Persona.class));
        verify(rabbitTemplate, never()).convertAndSend(
                anyString(),
                anyString(),
                any(PersonaGenerationTask.class)
        );
    }

    @Test
    @DisplayName("startPersonaGeneration - выброс исключения при несуществующем пользователе")
    void startPersonaGenerationUserNotFound() {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                personaService.startPersonaGeneration(USER_ID, request)
        );

        verify(userRepository).findById(USER_ID);
        verify(personaRepository, never()).save(any(Persona.class));
        verify(rabbitTemplate, never()).convertAndSend(
                anyString(),
                anyString(),
                any(PersonaGenerationTask.class)
        );
    }

    @Test
    @DisplayName("startPersonaGeneration - создаваемая персона имеет статус GENERATING")
    void startPersonaGenerationStatusIsGenerating() {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);
        User user = new User();
        user.setId(USER_ID);

        Persona savedPersona = new Persona();
        savedPersona.setId(42L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(personaRepository.save(any(Persona.class))).thenReturn(savedPersona);

        personaService.startPersonaGeneration(USER_ID, request);

        verify(personaRepository).save(argThat(persona ->
                persona.getStatus() == Persona.PersonaStatus.GENERATING
        ));
    }

    @Test
    @DisplayName("startPersonaGeneration - созданная персона привязана к пользователю")
    void startPersonaGenerationPersonaLinkedToUser() {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);
        User user = new User();
        user.setId(USER_ID);

        Persona savedPersona = new Persona();
        savedPersona.setId(42L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(personaRepository.save(any(Persona.class))).thenReturn(savedPersona);

        personaService.startPersonaGeneration(USER_ID, request);

        verify(personaRepository).save(argThat(persona ->
                persona.getUser() != null && persona.getUser().getId().equals(USER_ID)
        ));
    }

    @Test
    @DisplayName("startPersonaGeneration - промпт не сохраняется в персону если он >= 2001 символов")
    void startPersonaGenerationRejectsPromptAt2001Chars() {
        PersonaGenerationRequest request = new PersonaGenerationRequest(LONG_PROMPT);

        assertThrows(RuntimeException.class, () ->
                personaService.startPersonaGeneration(USER_ID, request)
        );

        verify(personaRepository, never()).save(any(Persona.class));
    }

    @Test
    @DisplayName("startPersonaGeneration - множественные вызовы создают разные персоны")
    void startPersonaGenerationMultipleCallsCreateDifferentPersonas() {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);
        User user = new User();
        user.setId(USER_ID);

        Persona persona1 = new Persona();
        persona1.setId(1L);

        Persona persona2 = new Persona();
        persona2.setId(2L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(personaRepository.save(any(Persona.class)))
                .thenReturn(persona1)
                .thenReturn(persona2);

        Long id1 = personaService.startPersonaGeneration(USER_ID, request);
        Long id2 = personaService.startPersonaGeneration(USER_ID, request);

        assertNotEquals(id1, id2);
        assertEquals(1L, id1);
        assertEquals(2L, id2);
        verify(personaRepository, times(2)).save(any(Persona.class));
        verify(rabbitTemplate, times(2)).convertAndSend(anyString(), anyString(), any(PersonaGenerationTask.class));
    }

    @Test
    @DisplayName("startPersonaGeneration - промпт сохраняется в generationPrompt поле")
    void startPersonaGenerationSavesPromptInGenerationPromptField() {
        PersonaGenerationRequest request = new PersonaGenerationRequest(VALID_PROMPT);
        User user = new User();
        user.setId(USER_ID);

        Persona savedPersona = new Persona();
        savedPersona.setId(42L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(personaRepository.save(any(Persona.class))).thenReturn(savedPersona);

        personaService.startPersonaGeneration(USER_ID, request);

        verify(personaRepository).save(argThat(persona ->
                VALID_PROMPT.equals(persona.getGenerationPrompt())
        ));
    }
}
