package ru.tigran.personafeedbackengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.tigran.personafeedbackengine.model.Persona;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonaRepository extends JpaRepository<Persona, Long> {
    Optional<Persona> findByUserIdAndId(Long userId, Long personaId);
    List<Persona> findByUserIdAndIdIn(Long userId, List<Long> personaIds);

    /**
     * Поиск активной персоны по демографическим параметрам.
     *
     * Используется для переиспользования персон вместо генерации новых.
     * Ищет только активные (ACTIVE) и не удаленные персоны.
     *
     * @param userId       ID пользователя
     * @param gender       Пол ("male", "female", "other")
     * @param age          Возраст (точное значение)
     * @param region       Регион ("moscow", "spb", "regions")
     * @param incomeLevel  Уровень дохода ("low", "medium", "high")
     * @return Optional<Persona> - найденная персона или пустой Optional
     */
    @Query("""
        SELECT p FROM Persona p
        WHERE p.user.id = :userId
          AND p.demographicGender = :gender
          AND p.age = :age
          AND p.region = :region
          AND p.incomeLevel = :incomeLevel
          AND p.deleted = false
          AND p.status = 'ACTIVE'
        """)
    Optional<Persona> findByDemographics(
            @Param("userId") Long userId,
            @Param("gender") String gender,
            @Param("age") Integer age,
            @Param("region") String region,
            @Param("incomeLevel") String incomeLevel
    );

    /**
     * Поиск всех активных не удаленных персон пользователя.
     *
     * @param userId ID пользователя
     * @return Список активных персон
     */
    @Query("""
        SELECT p FROM Persona p
        WHERE p.user.id = :userId
          AND p.deleted = false
          AND p.status = 'ACTIVE'
        ORDER BY p.createdAt DESC
        """)
    List<Persona> findActiveByUserId(@Param("userId") Long userId);
}
