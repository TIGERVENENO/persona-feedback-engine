# dto/

## Purpose
Data transfer objects for API layer and message queue communication.

## Enum Classes (Validation & Dropdowns)

### Gender
Enum for persona gender values. Used for demographic validation and persona generation.
- Values: MALE, FEMALE, OTHER
- Provides `getValue()` (e.g., "male") and `getDisplayName()` (e.g., "Male")
- Static method `fromValue(String value)` for case-insensitive lookup

### Country
Enum for country codes (ISO 3166-1 alpha-2) used in persona demographic validation.
Contains major countries from different regions: Europe, Americas, Asia-Pacific, Middle East, Africa.
- Example values: RU (Russia), US (United States), GB (United Kingdom), DE (Germany), CN (China), etc.
- Provides `getCode()` and `getDisplayName()`
- Static method `fromCode(String code)` for case-insensitive lookup

### ActivitySphere
Enum for activity spheres / industries used in persona demographic validation.
Represents major industry sectors for persona generation.
- Values: IT, FINANCE, HEALTHCARE, EDUCATION, RETAIL, MANUFACTURING, CONSTRUCTION, ENTERTAINMENT, MEDIA, REAL_ESTATE, HOSPITALITY, TRANSPORTATION, ENERGY, AGRICULTURE, TELECOMMUNICATIONS, AUTOMOTIVE, FASHION, SPORTS, GOVERNMENT, LAW, CONSULTING, MARKETING, HUMAN_RESOURCES, RESEARCH, STUDENT, UNEMPLOYED, RETIRED, FREELANCE, OTHER
- Provides `getValue()` and `getDisplayName()`
- Static method `fromValue(String value)` for case-insensitive lookup

## API Request DTOs

### PersonaGenerationRequest
Structured persona generation request with comprehensive demographic and psychographic parameters.
Supports batch generation: generates multiple personas (default 6) based on specified characteristics.

**Fields:**
- `gender: Gender` (required, enum) - Gender of persona (MALE, FEMALE, OTHER)
- `country: Country` (required, enum) - Country (ISO 3166-1 alpha-2 code)
- `city: String` (required, 1-100 chars) - City name
- `minAge: Integer` (required, 18-120) - Minimum age for persona age range
- `maxAge: Integer` (required, 18-120, must be > minAge) - Maximum age for persona age range
- `activitySphere: ActivitySphere` (required, enum) - Industry/activity sphere (IT, FINANCE, HEALTHCARE, etc.)
- `profession: String` (optional, max 150 chars) - Specific profession/role (e.g., "Senior Software Engineer")
- `income: String` (optional, max 100 chars) - Income range (e.g., "$50k-$75k", "High")
- `interests: List<String>` (optional, max 10 items) - Array of interests/hobbies
- `additionalParams: String` (optional, max 500 chars) - Additional custom parameters
- `count: Integer` (optional, 1-10, default 6) - Number of personas to generate

**Usage Example:**
```json
{
  "gender": "MALE",
  "country": "RU",
  "city": "Moscow",
  "minAge": 30,
  "maxAge": 45,
  "activitySphere": "IT",
  "profession": "Senior Backend Developer",
  "income": "$60k-$80k",
  "interests": ["Photography", "Travel", "Technology"],
  "additionalParams": "Recently moved to Moscow, works in fintech startup",
  "count": 6
}
```

**Validation:**
- All required fields must be non-null
- Custom validation: maxAge must be > minAge
- count defaults to 6 if not specified
- Generated personas are batched and created asynchronously

### PersonaDemographics (Legacy)
Old structure for backward compatibility. Recommend using PersonaGenerationRequest instead.
- `age: String` - Age or age range (e.g., "30-40", "Senior")
- `gender: String` - Gender (e.g., "Male", "Female", "Non-binary")
- `location: String` - Geographic location (e.g., "New York, USA", "London, UK")
- `occupation: String` - Job title or occupation (e.g., "Software Engineer", "Teacher")
- `income: String` - Income range (e.g., "$50k-$75k", "Middle class")

### PersonaPsychographics (Legacy)
Old structure for backward compatibility.
- `values: String` - Core values (e.g., "Sustainability, innovation")
- `lifestyle: String` - Lifestyle description (e.g., "Active, tech-savvy")
- `painPoints: String` - Key pain points (e.g., "Limited time, budget constraints")

### ProductRequest
- `name: String` - Product name (required, 1-200 chars)
- `description: String` - Product description (optional, max 5000 chars)
- `price: BigDecimal` - Product price (optional, minimum 0.00)
- `currency: String` - ISO 4217 currency code (optional, exactly 3 chars: "USD", "RUB", "EUR", etc.)
- `category: String` - Product category (optional, max 100 chars)
- `keyFeatures: List<String>` - List of key product features (optional)

### FeedbackSessionRequest
Request for creating feedback session with two operating modes.

**Mode 1 - Explicit personas (old approach, backward compatible)**:
- `productIds: List<Long>` - Products to get feedback on (max 5, required)
- `personaIds: List<Long>` - Explicitly specified personas (1-5, optional)
- `language: String` - ISO 639-1 language code (EN, RU, FR, etc.) - required

**Mode 2 - Auto-generate personas (new bulk approach)**:
- `productIds: List<Long>` - Products to get feedback on (max 5, required)
- `targetAudience: TargetAudience` - Demographics for auto-generation (optional)
- `personaCount: Integer` - Number of personas to generate (3-7, default: 5, optional)
- `language: String` - ISO 639-1 language code (EN, RU, FR, etc.) - required

**Validation**: Exactly one mode must be used - either personaIds OR targetAudience must be provided, not both

### TargetAudience
Structured demographics for bulk persona generation.

- `genders: List<String>` - List of genders (["male", "female", "other"], 1-3 elements, required)
- `ageRanges: List<String>` - Age ranges (["18-25", "26-35", "36-45", "46+"], 1-4 elements, required)
- `regions: List<String>` - Regions (["moscow", "spb", "regions"], 1-3 elements, required)
- `incomes: List<String>` - Income levels (["low", "medium", "high"], 1-3 elements, required)

### PersonaVariation
Internal DTO for specific persona demographics combination.

- `gender: String` - Specific gender ("male", "female", "other")
- `age: Integer` - Exact age (e.g., 27, 34)
- `region: String` - Specific region ("moscow", "spb", "regions")
- `incomeLevel: String` - Income level ("low", "medium", "high")

### AggregatedInsights
AI-aggregated insights from completed feedback session.

- `averageScore: Double` - Average purchase intent (1-10)
- `purchaseIntentPercent: Integer` - Percentage of personas with purchaseIntent >= 7
- `keyThemes: List<KeyTheme>` - Main grouped themes from all feedback

**KeyTheme nested record**:
- `theme: String` - Theme description (e.g., "Price concerns")
- `mentions: Integer` - How many personas mentioned this theme

## API Response DTOs

### JobResponse
- `jobId: Long` - ID of created job (Persona or FeedbackSession)
- `status: String` - Initial status (e.g., "GENERATING", "PENDING")
- Generic response for all async operations

### PersonaResponse
Complete persona information including demographics, psychographics, and AI-generated details.

**Fields:**
- `id: Long` - Persona identifier
- `status: String` - GENERATING, ACTIVE, FAILED
- `name: String` - AI-generated full name
- `detailedDescription: String` - 150-200 word bio covering shopping habits, brand preferences, decision-making
- `productAttitudes: String` - How persona evaluates and decides on products
- `gender: String` - From request (male, female, non-binary)
- `country: String` - ISO 3166-1 alpha-2 code
- `city: String` - City name
- `minAge: Integer` - Minimum age from request
- `maxAge: Integer` - Maximum age from request
- `activitySphere: String` - Activity sphere from request
- `profession: String` - Specific profession/role (optional)
- `income: String` - Income range (optional)
- `interests: List<String>` - Array of interests/hobbies (optional)
- `additionalParams: String` - Additional custom parameters (optional)
- `ageGroup: String` - AI-extracted age group (e.g., "25-34", "35-44")
- `race: String` - AI-extracted race/ethnicity
- `avatarUrl: String` - Avatar URL if available
- `createdAt: LocalDateTime` - Creation timestamp

**Note:** Personas are ALWAYS generated in English for consistency, regardless of input language.

### ProductResponse
- `id: Long`
- `name: String`
- `description: String`
- `price: BigDecimal` - Product price
- `currency: String` - ISO 4217 currency code
- `category: String` - Product category
- `keyFeatures: List<String>` - List of key product features

### FeedbackSessionResponse
- `id: Long`
- `status: String`
- `language: String` - ISO 639-1 language code used for feedback generation
- `createdAt: LocalDateTime`
- `feedbackResults: List<FeedbackResultDTO>`
- `aggregatedInsights: AggregatedInsights` (nullable) - AI-aggregated insights, null if session not completed
- `pageNumber: Integer` (nullable) - 0-based page number when paginated, null if all results
- `pageSize: Integer` (nullable) - page size when paginated, null if all results
- `totalCount: Long` (nullable) - total count of feedback results, null if all results returned

### FeedbackResultDTO
- `id: Long`
- `feedbackText: String` - Detailed review in specified language (3-5 sentences)
- `purchaseIntent: Integer` - Purchase intent rating 1-10
- `keyConcerns: List<String>` - Array of 2-4 main concerns/hesitations
- `status: String` - PENDING, IN_PROGRESS, COMPLETED, FAILED
- `personaId: Long`
- `productId: Long`

### SessionStatusInfo
- Internal DTO for feedback session status aggregation
- Fields:
  - `completedCount: Long` - Number of completed feedback results
  - `failedCount: Long` - Number of failed feedback results
  - `totalCount: Long` - Total number of feedback results in session
- Usage: Used by `FeedbackResultRepository.getSessionStatus()` for atomic status calculation
- Not exposed in API responses

## Queue Message DTOs

### PersonaGenerationTask
- `personaId: Long` - ID of Persona entity to update
- `demographicsJson: String` - JSON string with demographics (age, gender, location, occupation, income)
- `psychographicsJson: String` - JSON string with psychographics (values, lifestyle, painPoints)

### FeedbackGenerationTask
- `resultId: Long` - ID of FeedbackResult to update
- `productId: Long` - Product being reviewed (ID used to load full entity with price, category, keyFeatures)
- `personaId: Long` - Persona providing feedback (ID used to load full entity with detailedDescription, productAttitudes)
- `language: String` - ISO 639-1 language code for feedback text (EN, RU, FR, etc.)

## Frontend Support DTOs

### EnumValueDTO
Single enum value option for frontend dropdowns/selects.
- `value: String` - The actual enum value (e.g., "male", "RU", "IT")
- `displayName: String` - Human-readable display name (e.g., "Male", "Russia", "Information Technology")

### EnumValuesResponse
Response containing all available enum values for persona creation.
Returned by GET /api/v1/personas/enums endpoint.
- `genders: List<EnumValueDTO>` - All available gender options
- `countries: List<EnumValueDTO>` - All available country options
- `activitySpheres: List<EnumValueDTO>` - All available activity sphere options

**Usage:** Frontend uses this to populate dropdown lists for gender, country, and activity sphere fields in persona creation form.
