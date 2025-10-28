# validation/

## Purpose
Custom validation annotations and validators for request data validation.

## Key Components

### @ISO6391LanguageCode
Custom validation annotation for ISO 639-1 language codes.

**Usage:**
```java
@ISO6391LanguageCode
String language;
```

**Validation Rules:**
- Must be exactly 2 characters long
- Must be a valid ISO 639-1 language code (EN, RU, FR, DE, ES, etc.)
- Case-insensitive (automatically converted to uppercase)
- Validated against Java's Locale.getISOLanguages() list

**Error Message:**
Default: "Language code must be a valid ISO 639-1 two-letter code"

### ISO6391LanguageCodeValidator
Validator implementation for @ISO6391LanguageCode annotation.

**Implementation:**
- Uses Java's built-in `Locale.getISOLanguages()` to get all valid ISO 639-1 codes
- Validates length and presence in ISO language list
- Converts input to uppercase for case-insensitive validation
- Returns `false` for null values or invalid codes

**Examples of Valid Codes:**
- EN (English)
- RU (Russian)
- FR (French)
- DE (German)
- ES (Spanish)
- JA (Japanese)
- ZH (Chinese)
- AR (Arabic)

## Integration
Used in:
- `FeedbackSessionRequest.language` - Validates language code for feedback generation requests
