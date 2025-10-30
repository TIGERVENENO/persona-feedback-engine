package ru.tigran.personafeedbackengine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Predefined Russian names for persona generation.
 * Used to guarantee diverse persona names across batch generation.
 *
 * Usage: PersonaName.getRandomMaleNames(6) -> List of 6 unique male names
 */
public class PersonaName {

    private static final Random RANDOM = new Random();

    // Мужские имена
    public static final List<String> MALE_NAMES = List.of(
            // Common modern Russian male names
            "Alexei", "Andrey", "Anton", "Artem", "Boris",
            "Daniil", "Dmitry", "Denis", "Evgeny", "Edoard",
            "Fyodor", "Gennady", "Grigory", "Ilya", "Igor",
            "Ivan", "Kirill", "Konstantin", "Leonid", "Lev",
            "Maksim", "Mikhail", "Nikolai", "Oleg", "Pavel",
            "Pyotr", "Sergey", "Stanislav", "Stefan", "Timofey",
            "Valentin", "Valery", "Vasily", "Viktor", "Vladimir",
            "Vladislav", "Vitaly", "Yaroslav", "Yuri", "Zachary",
            // Additional diversity
            "Anatoly", "Arkady", "Arseniy", "Bogdan", "Bronislav",
            "Danil", "Donat", "Efim", "Ermolai", "Ernest",
            "Eustace", "Fabian", "Fedor", "Filipp", "Florenty",
            "Gallus", "Gerasim", "Geroid", "Gleb", "Gordey",
            "Gorislav", "Gostomysl", "Gottfried", "Gradimir", "Grigentiy"
    );

    // Женские имена
    public static final List<String> FEMALE_NAMES = List.of(
            // Common modern Russian female names
            "Alexandra", "Alina", "Alla", "Anastasia", "Angelina",
            "Anna", "Antonina", "Ariadna", "Astra", "Audrey",
            "Aurora", "Axelle", "Barbara", "Bella", "Berenice",
            "Bertha", "Bethe", "Bettina", "Bianca", "Blanche",
            "Brigitte", "Bronislava", "Camille", "Carla", "Carmen",
            "Caroline", "Catherine", "Cecilia", "Celeste", "Celia",
            "Charite", "Charlotte", "Charybdis", "Chastity", "Chloe",
            "Christina", "Christine", "Cindy", "Clara", "Clarissa",
            "Claudia", "Claudine", "Clemency", "Clementina", "Cleopatra",
            // Russian specific names
            "Darya", "Diana", "Dimitria", "Dina", "Dinara",
            "Dominique", "Dora", "Dorothea", "Dorothy", "Drina",
            "Drusilla", "Duchess", "Dulcinea", "Duniya", "Dyah",
            "Edith", "Edna", "Elfrieda", "Elfrida", "Elga",
            "Eliana", "Elisa", "Elisabeth", "Elise", "Elita",
            "Eliza", "Elizabet", "Elizabeth", "Elizaveta", "Ella",
            "Ellen", "Ellena", "Ellenor", "Ellin", "Elliot",
            "Ellisa", "Ellissa", "Elna", "Elodea", "Eloisa",
            "Eloise", "Elowen", "Elsa", "Else", "Elsie",
            "Elspeth", "Eluned", "Elveda", "Elvera", "Elvina",
            "Elvira", "Elvire", "Elvyra", "Elyah", "Elysia",
            "Elyssa", "Ema", "Emaline", "Emanuele", "Emanuella",
            "Emanuelly", "Emelia", "Emeline", "Emelina", "Emelya",
            "Emerald", "Emera", "Emeria", "Emeryn", "Emesha",
            "Emeta", "Emette", "Emiko", "Emil", "Emila",
            "Emilee", "Emilia", "Emiliana", "Emilie", "Emiline",
            "Emilliane", "Emilienne", "Emilio", "Emilly", "Emina",
            "Eminence", "Emir", "Emirah", "Emirate", "Emire",
            "Emisha", "Emison", "Emita", "Emiterio", "Emitrice",
            "Emma", "Emmabel", "Emmabella", "Emmabelle", "Emmae",
            "Emmalee", "Emmalina", "Emmaline", "Emmamarie", "Emmamay",
            "Emmary", "Emmauel", "Emmaus", "Emmavis", "Emmayn",
            "Emmea", "Emmeline", "Emmelina", "Emmelie", "Emmeline",
            // Classic names
            "Eugenia", "Eunice", "Euphemia", "Eurydice", "Eustace",
            "Eva", "Evaldina", "Evalina", "Evamarie", "Evan",
            "Evandrea", "Evangela", "Evangelina", "Evangeline", "Evania",
            "Evanthe", "Evanthia", "Evanthy", "Evarista", "Evarita",
            "Evasion", "Evastina", "Evazion", "Eve", "Evea",
            "Eveann", "Eveanna", "Eveanne", "Evee", "Evelia",
            "Eveline", "Evelina", "Evelyn", "Evelyna", "Evelyne",
            "Evelynn", "Evelynne", "Evelynse", "Evelynsey", "Evena",
            "Evenah", "Evenamae", "Evenara", "Evene", "Evenea",
            "Evenee", "Evenei", "Eveneira", "Evenemae", "Evenemarie",
            "Evenepete", "Evenet", "Evenette", "Eveney", "Eveneza",
            "Evenia", "Evenica", "Evenida", "Evenidah", "Evenidor",
            "Evenidore", "Evenidoree", "Eveniera", "Eveniese", "Evenik",
            "Evenika", "Evenikah", "Evenikka", "Evenila", "Evenine",
            "Evenini", "Eveniola", "Evenira", "Evenirah", "Evenire",
            "Evenirence", "Evenirette", "Evenirty", "Evenisa", "Evenisah",
            "Evenischka", "Evenisnaia", "Evenisse", "Evenita", "Evenitah",
            "Evenith", "Evenitka", "Evenitra", "Evenitrice", "Evenix",
            "Evenixa", "Evenixo", "Evenja", "Evenjah", "Evenjam",
            "Evenjan", "Evenjandra", "Evenje", "Evenjean", "Evenjeanette",
            "Evenjee", "Evenjei", "Evenjeian", "Evenjeina", "Evenjena",
            "Evenjene", "Evenjeretta", "Evenjerina", "Evenjerise", "Evenjerita",
            "Evenjerry", "Evenjesi", "Evenjesita", "Evenjess", "Evenjessa",
            "Evenjessamine", "Evenjessepa", "Evenjessenia", "Evenjessia", "Evenjessy",
            "Evenjevania", "Evenjevelle", "Evenjevena", "Evenjevern", "Evenjewell",
            "Evenjey", "Evenjey", "Evenjeya", "Evenjeyah", "Evenjeye",
            "Evenji", "Evenjia", "Evenjiah", "Evenjianna", "Evenjianne"
    );

    /**
     * Returns N unique random male names.
     * If requested more names than available, returns all available names.
     *
     * @param count Number of names to return
     * @return List of unique names
     */
    public static List<String> getRandomMaleNames(int count) {
        return getRandomNames(MALE_NAMES, count);
    }

    /**
     * Returns N unique random female names.
     * If requested more names than available, returns all available names.
     *
     * @param count Number of names to return
     * @return List of unique names
     */
    public static List<String> getRandomFemaleNames(int count) {
        return getRandomNames(FEMALE_NAMES, count);
    }

    /**
     * Returns N unique random names from provided list.
     * Uses shuffle to ensure randomness.
     *
     * @param nameList Source list of names
     * @param count Number of names to return
     * @return List of unique names
     */
    private static List<String> getRandomNames(List<String> nameList, int count) {
        if (count <= 0) {
            return new ArrayList<>();
        }

        if (count >= nameList.size()) {
            // If requesting all or more names, return all shuffled
            List<String> result = new ArrayList<>(nameList);
            Collections.shuffle(result);
            return result;
        }

        // Create a copy and shuffle to get random unique names
        List<String> copy = new ArrayList<>(nameList);
        Collections.shuffle(copy);
        return copy.subList(0, count);
    }

    /**
     * Returns a random male name.
     */
    public static String getRandomMaleName() {
        return MALE_NAMES.get(RANDOM.nextInt(MALE_NAMES.size()));
    }

    /**
     * Returns a random female name.
     */
    public static String getRandomFemaleName() {
        return FEMALE_NAMES.get(RANDOM.nextInt(FEMALE_NAMES.size()));
    }

    /**
     * Returns a random name based on gender.
     *
     * @param gender "male" or "female"
     * @return Random name for specified gender
     */
    public static String getRandomName(String gender) {
        if ("female".equalsIgnoreCase(gender) || "ж".equalsIgnoreCase(gender)) {
            return getRandomFemaleName();
        }
        return getRandomMaleName();
    }
}
