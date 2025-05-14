# Cubeia Wallet Service

A Spring Boot-based wallet service that provides REST APIs for account creation, balance checks, and transfers between accounts.

## Project Overview

This application implements a simple wallet service with the following features:

- Create new accounts with zero balance
- Check account balances
- Transfer funds between accounts
- View transaction history for an account

The implementation ensures thread safety during concurrent transfers using pessimistic locking.

## Technologies

- Java 21
- Spring Boot 3.4.5
- Spring Data JPA
- H2 Database
- Validation
- Springdoc OpenAPI
- JaCoCo for test coverage

## Build Instructions

To build the application, run:

```bash
mvn clean verify
```

This will compile the code, run the tests (including integration tests), and generate a JaCoCo test coverage report at `target/site/jacoco/index.html`.

## Run Instructions

To run the application:

```bash
java -jar target/wallet-0.0.1-SNAPSHOT.jar
```

Or using Maven:

```bash
mvn spring-boot:run
```

The API will be available at http://localhost:8080

## API Documentation

The API documentation is available via Swagger UI at:

http://localhost:8080/swagger-ui.html

## API Endpoints

### Create Account

Creates a new account with zero balance.

```bash
curl -X POST http://localhost:8080/accounts -H "Content-Type: application/json"
```

### Get Account Balance

Retrieves the balance of an account.

```bash
curl -X GET http://localhost:8080/accounts/{accountId}/balance
```

### Transfer Funds

Transfers funds from one account to another.

```bash
curl -X POST http://localhost:8080/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 1,
    "toAccountId": 2,
    "amount": 100.00,
    "referenceId": "payment-123456"
  }'
```

The `referenceId` field is optional and enables idempotent transfers - multiple requests with the same `referenceId` will only execute the transfer once.

### Get Transaction by Reference ID

Retrieves a transaction by its reference ID.

```bash
curl -X GET http://localhost:8080/transactions/reference/{referenceId}
```

### Get Account Transactions

Retrieves all transactions involving an account.

```bash
curl -X GET http://localhost:8080/accounts/{accountId}/transactions
```

## Implementation Notes

- Pessimistic locking is used to ensure thread safety during concurrent transfers
- Idempotent transfers are supported via reference IDs to safely retry operations
- Comprehensive validation and error handling for API requests
- Detailed error responses with appropriate HTTP status codes
- Full test suite including unit tests and integration tests with concurrency testing

### Validation Service Architecture

The wallet application uses a dedicated `ValidationService` for centralizing validation logic:

1. **Benefits of a Centralized Validation Service**:
   - **DRY Principle**: Eliminates code duplication across controllers and service layers
   - **Separation of Concerns**: Cleanly separates validation logic from business processing
   - **Pre-Validation**: Allows for early validation before acquiring expensive resources like database locks
   - **Testability**: Makes validation rules easier to test in isolation
   - **Consistent Error Messages**: Ensures the same validation failures produce consistent error responses
   - **Security**: Centralizes input validation, reducing the risk of security vulnerabilities

2. **Implementation Approach**:
   - The `ValidationService` encapsulates validation logic for all transaction operations
   - It performs validation checks including account existence, currency matching, and amount validation
   - Returns validated entities to avoid redundant database lookups
   - Throws specific, descriptive exceptions when validation fails

3. **Performance Considerations**:
   - Pre-validation reduces database contention by failing early before acquiring locks
   - Validation results include necessary entity objects to avoid redundant queries
   - Careful design ensures validation doesn't duplicate work performed in the transaction processing

This approach significantly improves the maintainability and robustness of the application while providing a clean separation between validation concerns and core business logic.

### Idempotency Support

The wallet application implements idempotency for transfer operations:

1. **How it works**:
   - Clients can include an optional `referenceId` in transfer requests
   - If a transfer with the same `referenceId` is already processed, the system returns the existing transaction without executing a new transfer
   - This ensures that retried or duplicate requests don't result in multiple transfers

2. **Benefits**:
   - Safely retry failed network requests without risking duplicate transfers
   - Protect against accidental duplicate submissions
   - Ensure exactly-once semantics in distributed systems

3. **Implementation details**:
   - The `Transaction` model includes a `reference` field
   - The `TransactionRepository` provides methods to find transactions by reference ID
   - `TransactionService` checks for existing transactions with the same reference ID before processing
   - Duplicate requests with matching parameters return the existing transaction
   - Requests with the same reference ID but different parameters receive an error

4. **Verification**:
   - Idempotency is thoroughly tested in `TransactionIdempotencyTest` and `WalletControllerIdempotencyTest`
   - Tests verify that duplicate requests don't create duplicate transactions or affect balances multiple times

### Deadlock Prevention Strategy

The wallet application employs a robust deadlock prevention mechanism to ensure thread safety during concurrent transfers. The strategy is implemented in the `TransactionService`:

1. **Consistent Lock Ordering**: Accounts are always locked in a consistent order based on their IDs, regardless of whether they are the source or destination account.

2. **Implementation Details**:
   - When transferring between accounts, the system compares their IDs
   - If `fromAccountId <= toAccountId`, locks are acquired in the natural order (from → to)
   - If `fromAccountId > toAccountId`, locks are acquired in the reversed order (to → first)
   - This ensures that multiple concurrent transfers involving the same accounts always acquire locks in the same order

3. **Why It Works**:
   - In financial systems, deadlocks typically occur when two concurrent operations try to lock the same resources in different orders
   - Example potential deadlock scenario:
     * Thread 1: Transfer A → B (would naturally lock A first, then B)
     * Thread 2: Transfer B → A (would naturally lock B first, then A)
   - By enforcing consistent lock ordering based on account ID comparison, this deadlocking scenario is prevented

4. **Verification**:
   - The deadlock prevention mechanism is thoroughly tested in `DeadlockPreventionTest`
   - This test creates concurrent transfers in opposite directions between the same accounts
   - It verifies that all transfers complete successfully without deadlocking

This approach ensures transaction consistency and correctness while maintaining high throughput for concurrent operations.

### Account Type Design

The wallet application uses a sealed interface pattern for the `AccountType` hierarchy:

1. **Why a sealed interface with singleton implementations?**
   - Type Safety: Sealed interface ensures all account types are known at compile time
   - Extensibility: New account types can be added by extending the permitted list
   - Singleton Pattern: Each account type is a singleton to ensure instance equality checks work
   - Pattern Matching: Enables exhaustive pattern matching in switch expressions

2. **Real-world Applications**:
   - In a production system, different account types would have additional properties and behaviors:
     * Bonus accounts might have expiration dates and special withdrawal rules
     * Pending accounts could have auto-conversion logic after settlement
     * Jackpot accounts might have regulatory restrictions on withdrawals
   - Having distinct types allows for type-specific validation and business logic

3. **Implementation Details**:
   - Each account type is implemented as a permitted class of the sealed interface
   - Singleton instances ensure reliable equality checks
   - The sealed nature provides compile-time guarantees about all possible subtypes

## Test Coverage and Strategy

The wallet application maintains high test coverage (94%) to ensure reliability and robustness. The test approach includes:

1. **Unit tests** for isolated components
2. **Integration tests** that test components with their dependencies

### Test Coverage Strategy

We prioritize stable, reliable testing over artificially inflated coverage numbers. The test suite is designed to:

- Verify core business logic through standard unit tests
- Test transaction functionality and concurrency with integration tests
- Validate exception handling through dedicated test cases

The functionality previously tested with complex mocking techniques is now fully covered by integration tests (TransactionDirectTest, DirectExecutionExceptionTest, and TransactionServiceIntegrationTest) that provide more reliable testing.

### Running Tests

- To run all tests: `mvn test`
- To run specific tests: `mvn test -Dtest=TestClassName`
- To generate coverage report: `mvn clean verify`

The coverage report is generated in `target/site/jacoco/index.html` and can be viewed in any browser. 