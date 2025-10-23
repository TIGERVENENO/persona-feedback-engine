# persona-feedback-engine

## Overview
Core package for the Persona Feedback Engine - a Spring Boot 3.x asynchronous service for generating AI personas and simulating feedback on products.

## Architecture
- **Modular Monolith** design with asynchronous processing via RabbitMQ
- **Two-stage workflow**: Persona Generation (heavy, cacheable) → Feedback Generation (lightweight, frequent)
- **Message-driven** with decoupled task consumers
- **Caching layer** for persona reusability based on generation prompts

## Package Structure
- `config/` - Spring configuration for RabbitMQ, Redis, RestClient, Cache
- `controller/` - REST API endpoints for personas and feedback sessions
- `dto/` - Request/response DTOs and queue payloads
- `model/` - JPA entities (User, Product, Persona, FeedbackSession, FeedbackResult)
- `repository/` - Spring Data JPA repositories
- `service/` - Business logic and queue message producers
- `queue/` - Message consumers for async task processing
- `exception/` - Custom exceptions and global error handling

## Key Design Decisions
1. **User-scoped data** - All entities strictly isolated by user_id
2. **Prompt-based caching** - Personas cached by generation prompt for reusability
3. **Simple authentication** - X-User-Id header for MVP
4. **Structured errors** - Consistent error response format with error codes
5. **No complex retry logic** - Failed tasks transition to FAILED state without retry queue
