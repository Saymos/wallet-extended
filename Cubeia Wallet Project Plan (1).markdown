# CONTEXT IMPORTANT

I'm a software developer with 6-7 years of Java experience, currently seeking a new job. I've been given this coding challenge by Cubeia, an iGaming company, as part of their hiring process. I'll soon have a follow-up interview with Cubeia, potentially including a senior architect and a co-founder, so I want my solution to be robust, well-documented, and aligned with production-grade practices. My goal here is to use Cursor to generate almost all code with AI and then just review and ensure it looks good. My steps are to use this project plan to give proper context for the LLM and then use a well detailed task list for the LLM to follow. 


# Cubeia Wallet Project Plan

## Original Assignment Text from Cubeia

**Java Wallet Coding Challenge**  
**The task**  
Implement a basic bookkeeping (accounting) application that keeps track of funds. Also called a "wallet" in online gaming terminology.  
**Deliverables**  
Link to GitHub repository (or similar) or a zip-archive. Instructions on how to build and run. Basic API documentation.  
**Description & Requirements**  
Implement a basic bookkeeping service that handles monetary transactions and keeps track of account balances. This is similar to a normal bank account.  
The service should have at least the following API methods:  
1. get balance - return the balance for an account  
2. transfer - transfer funds to or from an account  
3. list transactions - list transaction entries for an account  
4. create account (optional) - create an account, this can be done implicitly when creating transactions or explicitly  
The implementation should be a HTTP server exposing the API using REST. No UI is required. We will be using curl or POSTman to test the API.  
**Implementation**  
You can choose the effort put into the test yourself. We estimate it to take around 4 hours depending on experience and scope. The code needs to be thread safe and should work in an environment where we might be running a cluster of wallet servers. Implementation shortcuts are acceptable as long as they are documented and the proper solution is outlined.  
Keep this in mind:  
- Correctness is important - it should never be possible to get an incorrect balance of an account  
- The code should be readable and easy to follow  
- Thread safety and concurrency will be considered as an important aspect.  
**Technology**  
- Java 17+  
- REST framework, preferably Spring Boot  
- Avoid using Project Lombok  



## Project Plan

### ⚙️ Tech Stack
- **Java 21**: Latest LTS version with virtual threads for efficient concurrency.
- **Spring Boot 3.4.5**: Modern framework for RESTful services and dependency management.
- **H2 In-Memory Database**: Simple and effective for development and testing.
- **Springdoc OpenAPI**: For generating interactive API documentation.
- **JUnit 5 & JaCoCo**: For unit/integration testing and code coverage analysis.

### 🛠️ Initial Setup
1. Use Spring Initializr to create a project with dependencies: Spring Web, Spring Data JPA, H2 Database, and Springdoc OpenAPI.
2. Configure `application.properties`:
   - Enable virtual threads: `spring.threads.virtual.enabled=true`.
   - Set up H2 database with in-memory mode.

### 🧩 Domain Modeling
- **Account Entity**:
  - Fields: `id` (Long), `balance` (BigDecimal), `version` (for optimistic locking if needed).
- **Transaction Entity**:
  - Fields: `id` (Long), `fromAccountId` (Long), `toAccountId` (Long), `amount` (BigDecimal), `timestamp` (LocalDateTime).

### 💾 Persistence
- Use Spring Data JPA to define repositories:
  - `AccountRepository` for account CRUD operations.
  - `TransactionRepository` for transaction storage and retrieval.
- Configure entities with JPA annotations (`@Entity`, `@Id`, etc.).

### 🔒 Concurrency Handling
- Use pessimistic locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) for transfer operations to ensure thread safety.
- Document potential trade-offs (e.g., performance impact under high load) and outline optimistic locking as an alternative for production.

### 📡 REST API Endpoints
- **POST /accounts**: Create a new account (optional explicit creation).
- **GET /accounts/{id}/balance**: Retrieve account balance.
- **POST /accounts/transfer**: Transfer funds between accounts (fromAccountId, toAccountId, amount).
- **GET /accounts/{id}/transactions**: List transactions for an account.

### ✅ Validation & Error Handling
- Use `@Valid` and Hibernate Validator annotations (e.g., `@NotNull`, `@Positive`) on request DTOs.
- Implement a global `@ControllerAdvice` for consistent error responses (e.g., 400 for invalid input, 404 for missing accounts).

### 🧪 Testing
- **Unit Tests**: Test service layer logic (e.g., balance calculation, transfer validation).
- **Integration Tests**: Test API endpoints with an in-memory database, including concurrent transfer scenarios.
- Use JaCoCo to ensure at least 80% code coverage.

### 📄 Documentation & Dev UX
- **README.md**: Include build instructions (`mvn clean install`), run instructions (`java -jar target/wallet.jar`), and example curl commands.
- **Swagger UI**: Enable via Springdoc OpenAPI for interactive API testing.

### ✨ Enhancements
- Add idempotency keys to the transfer endpoint to prevent duplicate transactions.
- Include basic logging with SLF4J for debugging and monitoring.

## Considerations and Next Steps

### Considerations
- **Concurrency**: Pessimistic locking ensures correctness but may bottleneck under high concurrency; consider sharding or distributed locks for production.
- **Scalability**: The current in-memory H2 setup is not suitable for clustering; a persistent database (e.g., PostgreSQL) would be needed.
- **Security**: No authentication is included; production would require API key or OAuth2 integration.
- **Performance**: Balance retrieval could be cached for frequently accessed accounts.

### Next Steps
- **Security**: Add Spring Security with JWT or OAuth2 to secure endpoints.
- **Database**: Migrate to a persistent, distributed database like PostgreSQL or MySQL.
- **Monitoring**: Integrate metrics (e.g., Spring Actuator) and logging to a centralized system.
- **Testing**: Expand concurrency tests with simulated high-load scenarios.

This plan ensures a robust, thread-safe implementation aligned with Cubeia's requirements while showcasing production-ready practices for your interview.

## Implementation Summary

Based on the completed tasks, here's a summary of what has been implemented in the Cubeia Wallet project:

### Domain Modeling & Architecture
- Implemented a double-entry bookkeeping system for accounting, moving from a simple Account model with direct balance management to a more sophisticated ledger-based system
- Created proper Account and Transaction entities with appropriate fields and relationships
- Added support for different Account Types (Main, System accounts)
- Implemented currency support as noted in the original plan

### Persistence & Database Integrity
- Set up Spring Data JPA repositories for all entities
- Added unique constraints to transaction references to prevent duplicates
- Implemented database indexes on frequently queried fields for better performance
- Added case-insensitive reference lookup to improve compatibility with external systems
- Configured H2 in-memory database as planned in the project setup

### Concurrency Handling
- Implemented thread-safe transaction processing with proper locking mechanisms
- Created tests to verify concurrent transfer operations work correctly
- Fixed transaction execution tests to work with the double-entry architecture

### API Endpoints
- Implemented the required REST API with Spring Web:
  - GET balance endpoint
  - Transfer funds endpoint
  - List transactions endpoint
  - Create account endpoint
- Added comprehensive validation and error handling

### Testing
- Created extensive unit and integration tests
- Fixed test architecture to align with double-entry bookkeeping
- Implemented specific tests for transaction idempotency
- Added system account balance tests
- Implemented controller-level tests for API functionality

### Documentation & Development Experience
- Added API documentation using SpringDoc OpenAPI
- Created a README with build and run instructions
- Added example curl commands for the API

### Additional Enhancements
- Implemented idempotency keys for transfer endpoints as mentioned in the enhancement section
- Added proper logging with SLF4J
- Implemented virtual threads for efficient concurrency, as noted in the tech stack

All core requirements from the initial project plan have been successfully implemented, along with architectural improvements for a more robust financial system using double-entry accounting.