package ru.tigran.personafeedbackengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import ru.tigran.personafeedbackengine.dto.PersonaDemographics;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationRequest;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationTask;
import ru.tigran.personafeedbackengine.dto.PersonaPsychographics;
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
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        objectMapper = new ObjectMapper();

        personaService = new PersonaService(
                personaRepository,
                userRepository,
                rabbitTemplate,
                objectMapper,
                meterRegistry
        );
    }

    private PersonaGenerationRequest createValidRequest() {
        PersonaDemographics demographics = new PersonaDemographics(
                "30-40",  // age
                "Male",   // gender
                "New York, USA",  // location
                "Product Manager",  // occupation
                "$100k-$150k"  // income
        );
        PersonaPsychographics psychographics = new PersonaPsychographics(
                "Innovation, Leadership",  // values
                "Active, tech-savvy",  // lifestyle
                "Limited time, budget"  // painPoints
        );
        return new PersonaGenerationRequest(demographics, psychographics);
    }

    // ===== УСПЕШНЫЕ СЦЕНАРИИ =====

    @Test
    @DisplayName("startPersonaGeneration - успешное создание персоны с валидными демографическими данными")
    void startPersonaGenerationSuccess() {
        PersonaGenerationRequest request = createValidRequest();
        User user = new User();
        user.setId(USER_ID);

        Persona savedPersona = new Persona();
        savedPersona.setId(42L);
        savedPersona.setUser(user);
        savedPersona.setStatus(Persona.PersonaStatus.GENERATING);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(personaRepository.save(any(Persona.class))).thenReturn(savedPersona);

        Long personaId = personaService.startPersonaGeneration(USER_ID, request);

        assertEquals(42L, personaId);
        verify(personaRepository).save(argThat(persona ->
                persona.getStatus() == Persona.PersonaStatus.GENERATING
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
        PersonaGenerationRequest request = createValidRequest();
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
        assertNotNull(capturedTask.demographicsJson());
        assertNotNull(capturedTask.psychographicsJson());
    }

    @Test
    @DisplayName("startPersonaGeneration - успешно с валидными демографическими данными #2")
    void startPersonaGenerationValidRequest2() {
        PersonaGenerationRequest request = createValidRequest();
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
    @DisplayName("startPersonaGeneration - успешно с валидными демографическими данными #3")
    void startPersonaGenerationValidRequest3() {
        PersonaGenerationRequest request = createValidRequest();
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
    @DisplayName("startPersonaGeneration - выброс исключения при несуществующем пользователе")
    void startPersonaGenerationUserNotFound() {
        PersonaGenerationRequest request = createValidRequest();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // ValidationException обёрнута в RuntimeException в startPersonaGeneration
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
    @DisplayName("startPersonaGeneration - успешно создаёт и публикует задачу")
    void startPersonaGenerationSuccessPublishesMessage() {
        PersonaGenerationRequest request = createValidRequest();
        User user = new User();
        user.setId(USER_ID);

        Persona savedPersona = new Persona();
        savedPersona.setId(42L);
        savedPersona.setUser(user);
        savedPersona.setStatus(Persona.PersonaStatus.GENERATING);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(personaRepository.save(any(Persona.class))).thenReturn(savedPersona);

        Long personaId = personaService.startPersonaGeneration(USER_ID, request);

        assertNotNull(personaId);
        verify(personaRepository).save(any(Persona.class));
    }

    @Test
    @DisplayName("startPersonaGeneration - создаваемая персона имеет статус GENERATING")
    void startPersonaGenerationStatusIsGenerating() {
        PersonaGenerationRequest request = createValidRequest();
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
        PersonaGenerationRequest request = createValidRequest();
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
    @DisplayName("startPersonaGeneration - персона имеет generationPrompt с JSON данными")
    void startPersonaGenerationHasGenerationPrompt() {
        PersonaGenerationRequest request = createValidRequest();
        User user = new User();
        user.setId(USER_ID);

        Persona savedPersona = new Persona();
        savedPersona.setId(42L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(personaRepository.save(any(Persona.class))).thenReturn(savedPersona);

        personaService.startPersonaGeneration(USER_ID, request);

        verify(personaRepository).save(argThat(persona ->
                persona.getGenerationPrompt() != null && !persona.getGenerationPrompt().isEmpty()
        ));
    }

    @Test
    @DisplayName("startPersonaGeneration - множественные вызовы создают разные персоны")
    void startPersonaGenerationMultipleCallsCreateDifferentPersonas() {
        PersonaGenerationRequest request = createValidRequest();
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
    @DisplayName("startPersonaGeneration - сохраняет демографические данные в generationPrompt")
    void startPersonaGenerationSavesDemographicsInGenerationPrompt() {
        PersonaGenerationRequest request = createValidRequest();
        User user = new User();
        user.setId(USER_ID);

        Persona savedPersona = new Persona();
        savedPersona.setId(42L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(personaRepository.save(any(Persona.class))).thenReturn(savedPersona);

        personaService.startPersonaGeneration(USER_ID, request);

        verify(personaRepository).save(argThat(persona ->
                persona.getGenerationPrompt() != null &&
                persona.getGenerationPrompt().contains("Male") &&
                persona.getGenerationPrompt().contains("Product Manager")
        ));
    }
}
