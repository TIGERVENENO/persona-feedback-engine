package ru.tigran.personafeedbackengine.dto;

/**
 * Enum for country codes (ISO 3166-1 alpha-2) used in persona demographic validation.
 * Contains major countries from different regions worldwide.
 */
public enum Country {
    // Europe
    RU("RU", "Russia"),
    KZ("KZ", "Kazakhstan"),
    BY("BY", "Belarus"),
    UA("UA", "Ukraine"),
    GB("GB", "United Kingdom"),
    FR("FR", "France"),
    DE("DE", "Germany"),
    IT("IT", "Italy"),
    ES("ES", "Spain"),
    NL("NL", "Netherlands"),
    BE("BE", "Belgium"),
    CH("CH", "Switzerland"),
    AT("AT", "Austria"),
    PL("PL", "Poland"),
    CZ("CZ", "Czech Republic"),
    SE("SE", "Sweden"),
    NO("NO", "Norway"),
    DK("DK", "Denmark"),
    FI("FI", "Finland"),
    IE("IE", "Ireland"),
    GR("GR", "Greece"),
    PT("PT", "Portugal"),

    // Americas
    US("US", "United States"),
    CA("CA", "Canada"),
    MX("MX", "Mexico"),
    BR("BR", "Brazil"),
    AR("AR", "Argentina"),
    CL("CL", "Chile"),
    CO("CO", "Colombia"),
    PE("PE", "Peru"),

    // Asia-Pacific
    CN("CN", "China"),
    JP("JP", "Japan"),
    IN("IN", "India"),
    KR("KR", "South Korea"),
    TH("TH", "Thailand"),
    VN("VN", "Vietnam"),
    MY("MY", "Malaysia"),
    SG("SG", "Singapore"),
    ID("ID", "Indonesia"),
    PH("PH", "Philippines"),
    AU("AU", "Australia"),
    NZ("NZ", "New Zealand"),

    // Middle East & Central Asia
    TJ("TJ", "Tajikistan"),
    KG("KG", "Kyrgyzstan"),
    UZ("UZ", "Uzbekistan"),
    TM("TM", "Turkmenistan"),
    AZ("AZ", "Azerbaijan"),
    TR("TR", "Turkey"),
    SA("SA", "Saudi Arabia"),
    AE("AE", "United Arab Emirates"),
    IL("IL", "Israel"),

    // Africa
    ZA("ZA", "South Africa"),
    EG("EG", "Egypt"),
    NG("NG", "Nigeria"),
    KE("KE", "Kenya");

    private final String code;
    private final String displayName;

    Country(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get Country enum by ISO 3166-1 alpha-2 code (case-insensitive)
     * @param code the country code (e.g., "RU", "US", "GB")
     * @return Country enum or throws IllegalArgumentException if not found
     */
    public static Country fromCode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Country code cannot be null or empty");
        }
        for (Country country : Country.values()) {
            if (country.code.equalsIgnoreCase(code)) {
                return country;
            }
        }
        throw new IllegalArgumentException("Invalid country code: " + code);
    }
}
