package ru.tigran.personafeedbackengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.tigran.personafeedbackengine.model.Persona;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonaRepository extends JpaRepository<Persona, Long> {
    Optional<Persona> findByUserIdAndId(Long userId, Long personaId);
    List<Persona> findByUserIdAndIdIn(Long userId, List<Long> personaIds);
}
