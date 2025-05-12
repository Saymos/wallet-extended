# Cubeia Wallet Task List for Cursor

This task list guides you through implementing the Cubeia Wallet application using a test-first approach. Follow the steps in order. After completing each task or sub-task, update this list by marking it as `[x]` and proceed to the next unfinished item.

## Task List

### 1. Set Up Project
- [ ] **Prompt**: "Set up a new Spring Boot project using Spring Initializr with these dependencies: Spring Web, Spring Data JPA, H2 Database, Spring Validation, and Springdoc OpenAPI. Use Java 21 and Maven. Add JaCoCo for test coverage by including `<jacoco-maven-plugin>` in `pom.xml` (ensure it's configured to run during the `verify` phase). Enable virtual threads by adding `spring.threads.virtual.enabled=true` to `application.properties`. After completing this task, update this task list by marking this task as `[x]`, save the changes, and proceed to the next unfinished task."
- **Files**: `pom.xml`, `application.properties`

### 2. Define Domain Models
- [x] **Prompt**: "Create two entity classes: `Account` in `model/Account.java` with fields `id` (Long, auto-generated) and `balance` (BigDecimal); and `Transaction` in `model/Transaction.java` with fields `id` (Long, auto-generated), `fromAccountId` (Long), `toAccountId` (Long), `amount` (BigDecimal), and `timestamp` (LocalDateTime, set automatically on creation). Use JPA annotations (`@Entity`, `@Id`, `@GeneratedValue`, `@Column`, `@CreationTimestamp` or similar for timestamp) for database mapping. Ensure `BigDecimal` has appropriate precision/scale if necessary (e.g., `@Column(precision = 19, scale = 4)`). After completing this task, update this task list by marking this task as `[x]`, save the changes, and proceed to the next unfinished task."
- **Files**: `model/Account.java`, `model/Transaction.java`

### 3. Set Up Persistence
- [x] **Prompt**: "Create two repository interfaces: `AccountRepository` in `repository/AccountRepository.java` and `TransactionRepository` in `repository/TransactionRepository.java`, both extending `JpaRepository` with appropriate type parameters (`Account, Long` and `Transaction, Long`). Update `application.properties` with H2 settings: `spring.datasource.url=jdbc:h2:mem:wallet;DB_CLOSE_DELAY=-1`, `spring.jpa.show-sql=true`, `spring.jpa.hibernate.ddl-auto=update`. After completing this task, update this task list by marking this task as `[x]`, save the changes, and proceed to the next unfinished task."
- **Files**: `repository/AccountRepository.java`, `repository/TransactionRepository.java`, `application.properties`

### 4. Write Account Service Tests
- [x] **Prompt**: "Create `AccountServiceTest.java` in `service/` under the test directory (`src/test/java/your/package/service`). Write unit tests using JUnit 5 and Mockito. Test the following scenarios: 1) `createAccount` successfully creates an account with an initial balance of zero. 2) `getBalance` returns the correct balance for an existing account. 3) `getBalance` throws a specific custom exception (e.g., `AccountNotFoundException`) when attempting to retrieve a nonexistent account. Mock `AccountRepository` with `@Mock` and inject it into an instance of `AccountService` with `@InjectMocks`. After completing this task, update this task list by marking this task as `[x]`, save the changes, and proceed to the next unfinished task."
- **Files**: `service/AccountServiceTest.java`, potentially `exception/AccountNotFoundException.java`

### 5. Implement Account Service & Verify
- [x] **5a. Implement:** **Prompt**: "Create `AccountService.java` in `service/` under the main directory (`src/main/java/your/package/service`). Implement the class with the `@Service` annotation to satisfy the requirements from the tests in task 4. Include methods: `createAccount()` that saves a new `Account` with zero balance using `AccountRepository`, and `getBalance(Long accountId)` that fetches the account and returns its balance, throwing `AccountNotFoundException` if the account isn't found. Autowire `AccountRepository`. Create `AccountNotFoundException` if it doesn't exist yet."
    - **Files**: `service/AccountService.java`, `exception/AccountNotFoundException.java`
- [x] **5b. Test & Debug:** **Prompt**: "Run the tests in `AccountServiceTest.java` (e.g., via IDE or `mvn test -Dtest=AccountServiceTest`). If any tests fail, analyze the errors and rewrite the implementation in `AccountService.java` until all tests pass. Repeat this step until all the tests pass"
    - **Files**: `service/AccountService.java`

### 6. Write Transaction Service Tests
- [x] **Prompt**: "Create `TransactionServiceTest.java` in `service/` under the test directory. Write unit tests using JUnit 5 and Mockito. Test the `transfer` method for the following scenarios: 1) A successful transfer correctly deducts from the sender and adds to the receiver, and creates a `Transaction` record. 2) A transfer throws a specific custom exception (e.g., `InsufficientFundsException`) if the sender's balance is too low. 3) A transfer throws `AccountNotFoundException` if either the sender or receiver account does not exist. 4) Consider mocking concurrent access if possible in unit tests, or primarily rely on integration tests for full concurrency validation (Task 8). Mock `AccountRepository` and `TransactionRepository`. After completing this task, update this task list by marking this task as `[x]`, save the changes, and proceed to the next unfinished task."
- **Files**: `service/TransactionServiceTest.java`, potentially `exception/InsufficientFundsException.java`

### 7. Implement Transaction Service & Verify
- [x] **7a. Implement:** **Prompt**: "Create `TransactionService.java` in `service/` under the main directory. Implement the class with `@Service`. Implement the `transfer(Long fromAccountId, Long toAccountId, BigDecimal amount)` method. This method must:
    1.  Be annotated with `@Transactional` to ensure atomicity.
    2.  Fetch both the sender and receiver `Account` entities using `AccountRepository`. Handle `AccountNotFoundException` if either is missing.
    3.  **Crucially, ensure pessimistic locking** is applied when fetching the accounts to prevent race conditions during concurrent transfers. This might involve defining a custom repository method with `@Lock(LockModeType.PESSIMISTIC_WRITE)` and calling that, or ensuring the default `findById` respects the lock mode within the `@Transactional` context (verify based on JPA provider behavior, but explicitly requesting the lock via a custom method is often clearer).
    4.  Check if the sender has sufficient funds. If not, throw `InsufficientFundsException`.
    5.  Update the balances on both account objects.
    6.  Save both updated account objects using `AccountRepository`.
    7.  Create and save a `Transaction` record using `TransactionRepository`.
    Autowire `AccountRepository` and `TransactionRepository`. Create `InsufficientFundsException` if needed."
    - **Files**: `service/TransactionService.java`, `exception/InsufficientFundsException.java`, potentially update `repository/AccountRepository.java`
- [x] **7b. Test & Debug:** **Prompt**: "Run the tests in `TransactionServiceTest.java`. If any tests fail, analyze the errors and and rewrite the implementation in `TransactionService.java` until all tests pass."
    - **Files**: `service/TransactionService.java`

### 8. Write Wallet Controller Tests (Integration Tests)
- [ ] **Prompt**: "Create `WalletControllerTest.java` in `controller/` under the test directory. Use `@SpringBootTest` with a web environment (e.g., `WebEnvironment.RANDOM_PORT`) and `TestRestTemplate` or `MockMvc` for integration testing against an in-memory H2 database. Write tests for the REST endpoints:
    1.  `POST /accounts`: Successfully creates an account, returns 201 status and account details/ID.
    2.  `GET /accounts/{id}/balance`: Returns correct balance for an existing account (200 OK). Returns 404 for non-existent account.
    3.  `POST /transfers`: Executes a valid transfer, returns 200 OK or 204 No Content. Test invalid transfers: non-existent accounts (404), insufficient funds (400 or other appropriate error), invalid amount (400).
    4.  `GET /accounts/{id}/transactions`: Returns a list of transactions for an account (200 OK). Returns 404 for non-existent account.
    5.  **Concurrency Test:** Implement a specific test case that simulates concurrent transfer requests to the same account(s). Use Java's `ExecutorService` and `CountDownLatch` to initiate multiple `POST /transfers` calls simultaneously. Verify that the final account balances are correct, demonstrating the effectiveness of the pessimistic locking.
    After completing this task, update this task list by marking this task as `[x]`, save the changes, and proceed to the next unfinished task."
- **Files**: `controller/WalletControllerTest.java`

### 9. Implement Wallet Controller & Verify
- [ ] **9a. Implement:** **Prompt**: "Create `WalletController.java` in `controller/` under the main directory. Implement the class with `@RestController` and `@RequestMapping("/accounts")` (or just `/` if preferred). Define handler methods for the endpoints tested in task 8:
    1.  `POST /` (or `/accounts`): Takes necessary info (if any, maybe just creates an empty one), calls `AccountService.createAccount`, returns appropriate response (e.g., `ResponseEntity<AccountDto>`).
    2.  `GET /{id}/balance`: Calls `AccountService.getBalance`, returns balance (e.g., `ResponseEntity<BigDecimal>`).
    3.  `POST /transfers` (note: might be better at top level `/transfers` or keep as `/accounts/transfers`): Takes a `TransferRequestDto` (containing `fromAccountId`, `toAccountId`, `amount`) in the request body. Calls `TransactionService.transfer`. Return `ResponseEntity<Void>` or similar.
    4.  `GET /{id}/transactions`: Calls `TransactionService` (needs a method like `findTransactionsByAccountId`), returns a list of `TransactionDto`.
    Create necessary DTOs: `TransferRequestDto` (with validation annotations), `TransactionDto`, `AccountDto` (if needed for creation response). Autowire `AccountService` and `TransactionService`."
    - **Files**: `controller/WalletController.java`, `dto/TransferRequestDto.java`, `dto/TransactionDto.java`, `dto/AccountDto.java` (optional), needs update to `TransactionService.java` for listing transactions.
- [ ] **9b. Test & Debug:** **Prompt**: "Run the integration tests in `WalletControllerTest.java` (e.g., via IDE or `mvn verify` which should include integration tests). If any tests fail, analyze the errors and and rewrite the implementation in `WalletController.java`, relevant DTOs, or services until all tests pass."
    - **Files**: `controller/WalletController.java`, `dto/*`, `service/*`

### 10. Add Validation and Error Handling
- [ ] **Prompt**: "Ensure validation annotations (`@NotNull`, `@Positive`, etc.) are present on `TransferRequestDto`. Create `GlobalExceptionHandler.java` in an `exception` or `controller` package with `@ControllerAdvice`. Implement `@ExceptionHandler` methods for your custom exceptions (`AccountNotFoundException`, `InsufficientFundsException`) and potentially common Spring exceptions (`MethodArgumentNotValidException` for DTO validation failures, `HttpRequestMethodNotSupportedException`, etc.). These handlers should return clear JSON error responses (e.g., using a consistent `ErrorResponse` DTO containing timestamp, status, error, message, path) and appropriate HTTP status codes (404, 400, etc.). After completing this task, update this task list by marking this task as `[x]`, save the changes, and proceed to the next unfinished task."
- **Files**: `dto/TransferRequestDto.java`, `exception/GlobalExceptionHandler.java`, `dto/ErrorResponse.java` (or similar)

### 11. Document the API
- [ ] **Prompt**: "Configure Springdoc OpenAPI if not already done. You might need a `OpenApiConfig.java` in `config/` with `@OpenAPIDefinition` to add general API info (title, version, description). Ensure controller methods and DTOs have basic annotations (`@Operation`, `@Parameter`, `@Schema`) if needed for clarity in Swagger UI. Update/Create `README.md` with: 1) Project overview. 2) Build instructions (`mvn clean install` or `mvn clean verify`). 3) Run instructions (`java -jar target/wallet-app-name.jar`). 4) Link to the Swagger UI endpoint (usually `/swagger-ui.html`). 5) Example `curl` commands for each API endpoint. After completing this task, update this task list by marking this task as `[x]`, save the changes, and proceed to the next unfinished task."
- **Files**: `config/OpenApiConfig.java` (optional), `controller/WalletController.java` (add annotations), `dto/*` (add annotations), `README.md`

### 12. Review and Refine
- [ ] **Prompt**: "Perform a final review of the entire project. Ensure:
    1. All requirements from the original assignment text are met.
    2. Code is clean, readable, and consistently formatted.
    3. Error handling is consistent.
    4. API documentation is accessible and accurate.
    5. Run `mvn clean verify`. This command should compile, run all unit and integration tests, and generate the JaCoCo code coverage report (check `target/site/jacoco/index.html`). Aim for >90% coverage.
    6. Perform some manual tests using `curl` or Postman against a running instance.
    Review, implement and fix any final fixes or refactorings needed. If you find anything else that you think might need to be fixed or further considerations about the project, inform me. After completing this task, mark it as `[x]`. The project should now be ready."
- **Files**: All
