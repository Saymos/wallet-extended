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
    "amount": 100.00
  }'
```

### Get Account Transactions

Retrieves all transactions involving an account.

```bash
curl -X GET http://localhost:8080/accounts/{accountId}/transactions
```

## Implementation Notes

- Pessimistic locking is used to ensure thread safety during concurrent transfers
- Comprehensive validation and error handling for API requests
- Detailed error responses with appropriate HTTP status codes
- Full test suite including unit tests and integration tests with concurrency testing 

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