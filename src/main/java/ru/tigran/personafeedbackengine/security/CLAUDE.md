# security/

## Purpose
JWT-based authentication and authorization for Spring Security integration.
Implements stateless user authentication with email/password credentials stored securely in database.

## Key Classes

### JwtTokenProvider
Service for JWT token generation and validation.

**Methods:**
- `generateToken(Long userId) → String`
  - Generates JWT token with user ID as subject
  - Uses HMAC-SHA256 signing algorithm
  - Token expiration configurable via `app.jwt.expiration-hours` (default: 24 hours)
  - Returns JWT token string ready for Bearer authentication
  - **Usage**: Called after successful login/registration to create access token

- `getUserIdFromToken(String token) → Long`
  - Extracts and validates JWT token, returns user ID
  - Throws `io.jsonwebtoken.JwtException` if token invalid or expired
  - Performs cryptographic signature verification
  - **Usage**: Extracts userId from token for SecurityContext during request filtering

- `validateToken(String token) → boolean`
  - Validates JWT token without extracting claims
  - Returns true if valid, false on any validation error (expired, invalid signature, malformed)
  - Used for pre-validation checks before parsing
  - **Usage**: Optional validation before calling getUserIdFromToken()

- `extractTokenFromHeader(String authHeader) → String`
  - Parses Authorization header to extract Bearer token
  - Input format: "Bearer <token>" (standard HTTP convention)
  - Returns token string without "Bearer " prefix, or null if header invalid
  - **Usage**: Extract token from request headers in filter

**Key Implementation Details:**
- Secret key generated from configured string using HMAC-SHA256 algorithm
- Token expiration calculated: now + (expirationHours * 60 * 60 * 1000 milliseconds)
- Claims include: subject (userId), issuedAt, expiration
- Uses JJWT v0.12.3 library with new API: `Jwts.parser().verifyWith(key).build().parseSignedClaims()`

**Configuration:**
- `app.jwt.secret-key` - Secret string for signing (REQUIRED, set via environment variable)
- `app.jwt.expiration-hours` - Token expiration in hours (default: 24)

### JwtAuthenticationFilter
Request filter for JWT token validation and SecurityContext setup.

**Extends:** `OncePerRequestFilter` (ensures single execution per request)

**Methods:**
- `doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain) → void`
  - Extracts JWT token from Authorization header
  - Validates token using JwtTokenProvider
  - Creates Authentication with userId as principal
  - Sets SecurityContext for request processing
  - Continues filter chain on validation failure (no exception thrown)

**Key Implementation Details:**
- Extracts token using JwtTokenProvider.extractTokenFromHeader()
- Only processes requests with valid "Bearer " prefix
- Silently continues (logs at warn level) if validation fails
- Uses SecurityContextHolder.getContext().setAuthentication()
- Creates UsernamePasswordAuthenticationToken with userId as principal, null password, empty authorities

**Configuration:**
- Added to SecurityFilterChain before UsernamePasswordAuthenticationFilter
- Runs on every request to /api/v1/** endpoints

**Usage Pattern:**
```java
String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
// Token is extracted from Authorization: Bearer <token> header
// Filter validates and sets SecurityContext.getAuthentication().getPrincipal() = userId
Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
```

### SecurityConfig
Spring Security configuration for JWT-based stateless authentication.

**Key Settings:**
- **Session Management:** `SessionCreationPolicy.STATELESS` (no session cookies, only JWT)
- **CSRF Protection:** Disabled (stateless APIs don't need CSRF tokens)
- **Public Endpoints:**
  - `/api/v1/auth/register` - User registration
  - `/api/v1/auth/login` - User login
  - Actuator endpoints (health checks)
- **Protected Endpoints:** All `/api/v1/**` require authentication

**Beans Provided:**
- `PasswordEncoder` - BCryptPasswordEncoder with default work factor 10
  - Automatic salt generation per password
  - Used for password hashing during registration
  - Used for password matching during login
- `AuthenticationManager` - Built from security configuration

**Filter Chain Order:**
1. JwtAuthenticationFilter (extracts and validates JWT)
2. UsernamePasswordAuthenticationFilter (default Spring filter, not used in stateless mode)
3. Other standard filters

**Usage:**
- Automatically applied to all requests via @Configuration annotation
- No explicit filter registration needed

## Integration Points

### Controllers
- **PersonaController**: Extracts userId from `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`
- **FeedbackController**: Same pattern for user identification
- **AuthenticationController**: Calls AuthenticationService for login/register, returns JWT token

### Services
- **AuthenticationService**: Uses PasswordEncoder and JwtTokenProvider
- **UserRepository**: Uses findByEmail() and existsByEmail() for user lookup

### DTOs
- **RegisterRequest** - email, password with validations (@Email, @Size min:8 max:128)
- **LoginRequest** - email, password (same validations)
- **AuthenticationResponse** - userId, accessToken, tokenType="Bearer"

### Database
- **users table** - email (unique, not null), password_hash (not null, 60 char for BCrypt), is_active
- **idx_user_email** - Index for fast email lookups during login

## Design Principles

1. **Stateless Authentication** - No server-side session storage, all state in JWT token
2. **Secure Password Storage** - BCrypt hashing with automatic salt, never store plain text
3. **User Isolation** - All user operations scope to extracted userId from token
4. **Standard JWT Format** - Bearer token in Authorization header per RFC 6750
5. **Fail-Safe Filtering** - Invalid tokens don't throw exceptions, continue filter chain
6. **Public API Access** - Auth endpoints accessible without token for registration flow

## Common Patterns

**Register and Login Flow:**
```
POST /api/v1/auth/register with RegisterRequest
→ AuthenticationService.register() hashes password with BCrypt
→ Creates User entity with password_hash
→ JwtTokenProvider.generateToken(userId) creates JWT
→ Returns AuthenticationResponse with accessToken

POST /api/v1/auth/login with LoginRequest
→ AuthenticationService.login() finds user by email
→ passwordEncoder.matches() verifies password
→ JwtTokenProvider.generateToken(userId) creates JWT
→ Returns AuthenticationResponse with accessToken
```

**Protected Request Flow:**
```
GET /api/v1/feedback-sessions/{sessionId} with Authorization: Bearer <token>
→ JwtAuthenticationFilter.doFilterInternal() extracts token
→ JwtTokenProvider.validateToken() verifies signature and expiration
→ JwtTokenProvider.getUserIdFromToken() extracts userId
→ SecurityContextHolder.getContext().setAuthentication() stores userId
→ Controller accesses userId via SecurityContextHolder.getContext().getAuthentication().getPrincipal()
```

**Password Hashing:**
```java
PasswordEncoder encoder = new BCryptPasswordEncoder(); // work factor 10
String hash = encoder.encode("plainTextPassword");      // Automatic salt generation
boolean matches = encoder.matches("plainTextPassword", hash); // Constant-time comparison
```

## Error Handling

**Invalid/Expired Tokens:**
- Filter catches JwtException and continues (logs warning)
- Controller receives null from getAuthentication() or SecurityContext
- Should validate authentication exists before casting principal

**User Not Found (Login):**
- AuthenticationService throws ValidationException with error code INVALID_CREDENTIALS

**Email Already Exists (Register):**
- AuthenticationService throws ValidationException with error code EMAIL_ALREADY_EXISTS

**Validation Errors:**
- RegisterRequest/LoginRequest validation enforced by @Valid annotation
- Returns 400 Bad Request with validation error messages
