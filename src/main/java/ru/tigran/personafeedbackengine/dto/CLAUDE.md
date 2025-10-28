# dto/

## Purpose
Data transfer objects for API layer and message queue communication.

## API Request DTOs

### PersonaGenerationRequest
Structured persona generation request with demographics and psychographics.
- `demographics: PersonaDemographics` - Demographics data (required, validated)
- `psychographics: PersonaPsychographics` - Psychographics data (required, validated)

### PersonaDemographics
- `age: String` - Age or age range (e.g., "30-40", "Senior")
- `gender: String` - Gender (e.g., "Male", "Female", "Non-binary")
- `location: String` - Geographic location (e.g., "New York, USA", "London, UK")
- `occupation: String` - Job title or occupation (e.g., "Software Engineer", "Teacher")
- `income: String` - Income range (e.g., "$50k-$75k", "Middle class")

### PersonaPsychographics
- `values: String` - Core values (e.g., "Sustainability, innovation")
- `lifestyle: String` - Lifestyle description (e.g., "Active, tech-savvy")
- `painPoints: String` - Key pain points (e.g., "Limited time, budget constraints")

### ProductRequest
- `name: String` - Product name (required, 1-200 chars)
- `description: String` - Product description (optional, max 5000 chars)
- `price: BigDecimal` - Product price (optional, minimum 0.00)
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
- `id: Long`
- `name: String`
- `detailedDescription: String` - 150-200 word bio about shopping habits, brand preferences, decision-making
- `productAttitudes: String` - How persona evaluates and decides on products
- `status: String` - GENERATING, ACTIVE, FAILED

Note: Personas are ALWAYS generated in English for consistency, regardless of input language.

### ProductResponse
- `id: Long`
- `name: String`
- `description: String`
- `price: BigDecimal` - Product price
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
