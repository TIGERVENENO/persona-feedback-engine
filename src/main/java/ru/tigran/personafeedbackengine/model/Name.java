package ru.tigran.personafeedbackengine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Predefined names for persona generation by country.
 * Ensures personas have culturally appropriate names matching their country.
 *
 * Columns:
 * - id: Primary key
 * - name: The first name (no surnames)
 * - gender: "male" or "female"
 * - country: ISO 3166-1 alpha-2 country code (RU, US, GB, DE, etc.)
 *
 * Data: 10 male + 10 female names for 20 most popular countries = 400 records
 */
@Entity
@Table(name = "names", uniqueConstraints = {
        @UniqueConstraint(name = "uk_name_gender_country", columnNames = {"name", "gender", "country"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Name {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The first name (no surname)
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Gender: "male" or "female"
     */
    @Column(nullable = false, length = 10)
    private String gender;

    /**
     * ISO 3166-1 alpha-2 country code
     * Examples: RU, US, GB, DE, FR, IT, ES, BR, CN, IN, JP, KR, MX, CA, AU, NL, SE, PL, TR, NG
     */
    @Column(nullable = false, length = 2)
    private String country;

    /**
     * Constructor for convenience
     */
    public Name(String name, String gender, String country) {
        this.name = name;
        this.gender = gender;
        this.country = country;
    }

    @Override
    public String toString() {
        return name + " (" + gender + ", " + country + ")";
    }
}
