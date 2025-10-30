package ru.tigran.personafeedbackengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.tigran.personafeedbackengine.model.Name;

import java.util.List;

/**
 * Repository for predefined names by country and gender.
 */
@Repository
public interface NameRepository extends JpaRepository<Name, Long> {

    /**
     * Find all names for a specific country and gender.
     */
    List<Name> findByCountryAndGender(String country, String gender);

    /**
     * Find all names for a specific gender (any country).
     * Used as fallback when requested country not found.
     */
    List<Name> findByGender(String gender);

    /**
     * Check if names exist for a specific country.
     */
    boolean existsByCountry(String country);

    /**
     * Count names for a specific country.
     */
    long countByCountry(String country);

    /**
     * Get all distinct countries in the names table.
     */
    @Query("SELECT DISTINCT n.country FROM Name n ORDER BY n.country")
    List<String> findAllCountries();
}
