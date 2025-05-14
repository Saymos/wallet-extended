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

## Production Considerations

### Audit Trail Implementation for Financial Systems

1. **Comprehensive Audit Requirements**:
   - Every financial transaction must be immutable and traceable
   - Audit records should include who initiated the transaction, when, from where, and all transaction details
   - Changes to account status or configuration should also be audited

2. **Implementation Approaches**:
   - Store audit events in a separate audit table with foreign keys to transactions
   - Use database triggers or event listeners to capture audit data automatically
   - Consider storing audit logs in a dedicated audit database for regulatory compliance
   - Implement digital signatures for transaction records to prevent tampering

3. **Technologies to Consider**:
   - Append-only event stores for immutable transaction history
   - Spring Data Envers for automated auditing of JPA entities
   - Blockchain-based solutions for high-security requirements

### Logging Requirements for Financial Transactions

1. **Transaction Logging**:
   - Log detailed information for all financial operations
   - Include transaction IDs, account IDs, amounts, timestamps, and reference IDs
   - Structure logs in a consistent format for automated analysis
   - Ensure logs can be correlated across distributed systems

2. **Compliance Logging**:
   - Maintain logs according to financial regulatory requirements (PCI-DSS, SOX, etc.)
   - Implement log rotation and archiving policies
   - Ensure logs are tamper-evident and cannot be modified

3. **Security Logging**:
   - Track authentication/authorization events
   - Log all suspicious activities and access attempts
   - Implement alerting based on log patterns

4. **Implementation**:
   - Use a structured logging framework (SLF4J with Logback)
   - Configure MDC (Mapped Diagnostic Context) to include transaction IDs
   - Consider a centralized logging system like ELK Stack for production

### Performance Considerations for High Concurrency

1. **Database Optimization**:
   - Properly index transaction tables, especially on frequently queried columns
   - Consider separate read/write databases (CQRS pattern) for high traffic
   - Implement connection pooling with appropriate sizing

2. **Caching Strategies**:
   - Cache account balances for read-heavy scenarios
   - Use distributed caches (Redis/Hazelcast) in a clustered environment
   - Implement cache invalidation strategies for transaction consistency

3. **Thread Management**:
   - Optimize thread pool configurations for expected workloads
   - Monitor thread utilization under peak loads
   - Consider non-blocking I/O for high-throughput scenarios

4. **Load Testing**:
   - Test concurrent transfer scenarios with realistic traffic patterns
   - Measure response times and throughput under various loads
   - Identify bottlenecks and optimize accordingly

### Transaction Isolation Levels and Their Trade-offs

1. **READ UNCOMMITTED**:
   - Lowest isolation level; allows dirty reads
   - **Pros**: Highest concurrency, minimal locking overhead
   - **Cons**: Inconsistent reads, unsuitable for financial transactions
   - **Use case**: Fast approximate reporting where accuracy isn't critical

2. **READ COMMITTED** (Used in our application):
   - Prevents dirty reads but allows non-repeatable reads and phantom reads
   - **Pros**: Good balance of consistency and performance
   - **Cons**: May read different data if queried multiple times within the same transaction
   - **Use case**: General-purpose database operations where immediate consistency is important

3. **REPEATABLE READ**:
   - Prevents dirty and non-repeatable reads but allows phantom reads
   - **Pros**: Higher consistency guarantees
   - **Cons**: Increased locking, potential for deadlocks, reduced concurrency
   - **Use case**: Financial reporting where consistent reads are needed

4. **SERIALIZABLE**:
   - Highest isolation level; prevents all concurrency anomalies
   - **Pros**: Complete data consistency, suitable for complex financial operations
   - **Cons**: Significant performance impact, high potential for deadlocks
   - **Use case**: Critical financial operations requiring perfect consistency

Our application uses **READ COMMITTED** isolation with explicit pessimistic locking to provide a balance between performance and consistency appropriate for a wallet service.

### Clustering and Horizontal Scaling Approaches

1. **Stateless Application Design**:
   - Ensure the wallet service is stateless for horizontal scaling
   - Store all session data in a distributed cache or database
   - Use sticky sessions for performance when needed

2. **Database Clustering**:
   - Implement master-slave replication for read scalability
   - Consider sharding for transaction and account data based on account ID
   - Use a distributed transaction manager for cross-shard transactions

3. **Load Balancing**:
   - Deploy multiple service instances behind a load balancer
   - Configure health checks to detect and remove unhealthy instances
   - Implement appropriate session affinity policies

4. **Distributed Locking**:
   - Replace in-memory locks with distributed lock solutions (Redis, ZooKeeper)
   - Implement lock timeouts to prevent deadlocks in distributed environments
   - Consider optimistic locking for read-heavy workloads

5. **Monitoring and Auto-scaling**:
   - Implement metrics collection for service health and performance
   - Set up auto-scaling based on load patterns
   - Configure alerts for abnormal behavior

### The Use of Virtual Threads and Their Benefits

1. **What Are Virtual Threads?**:
   - Lightweight user-mode threads introduced in Java 21
   - Managed by the JVM rather than the operating system
   - Enable high-concurrency applications without thread pool tuning

2. **Benefits for a Wallet Application**:
   - **Improved Throughput**: Can handle many more concurrent transactions
   - **Reduced Memory Footprint**: Virtual threads use significantly less memory than platform threads
   - **Simplified Concurrency Model**: No need for complex thread pool configurations
   - **Better Resource Utilization**: Reduces wasted CPU time on blocked threads
   - **Simplified Code**: Can write straightforward blocking code that scales well

3. **Implementation in Our Application**:
   - Enabled via `spring.threads.virtual.enabled=true` in application properties
   - No additional code changes needed when using Spring Boot 3.4+
   - Each incoming request automatically gets its own virtual thread
   - Allows the application to handle thousands of concurrent transfers efficiently

4. **Considerations**:
   - Thread-local variables behave differently with virtual threads
   - Some libraries may not be fully compatible with virtual threads
   - Debugging can be more complex with thousands of threads

### Security Considerations for a Financial API

1. **Authentication and Authorization**:
   - Implement strong authentication (OAuth 2.0, JWT, mTLS)
   - Use role-based access control for different API operations
   - Enforce IP whitelisting for sensitive operations
   - Implement MFA for high-value transactions

2. **Data Protection**:
   - Encrypt sensitive data at rest and in transit (TLS 1.3+)
   - Implement proper key management policies
   - Use parameterized queries to prevent SQL injection
   - Sanitize all inputs to prevent XSS and other injection attacks

3. **API Security**:
   - Implement rate limiting to prevent DoS attacks
   - Set up API gateways with advanced security features
   - Use API keys or client certificates for service-to-service communication
   - Validate all request parameters and enforce strict API contracts

4. **Transaction Security**:
   - Implement transaction signing for non-repudiation
   - Use idempotency keys to prevent duplicate transactions
   - Set up transaction amount limits and velocity checks
   - Implement fraud detection algorithms

5. **Compliance**:
   - Ensure GDPR compliance for personal data
   - Implement PCI-DSS requirements for payment processing
   - Follow financial industry regulations (SOX, PSD2, etc.)
   - Regular security audits and penetration testing

## Architecture Comparisons

### Pessimistic Locking vs. Optimistic Locking

#### Pessimistic Locking (Current Implementation)

**Pros**:
- Guarantees data consistency in concurrent environments
- Prevents conflicts before they occur
- Simpler error handling (no retry logic needed)
- Better for high-contention scenarios with frequent conflicts

**Cons**:
- Can lead to reduced throughput under high concurrency
- Potential for deadlocks if not managed properly
- Higher database resource utilization
- Locks may be held longer than necessary

**Implementation in our application**:
- Uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` with JPA
- Accounts are locked in a consistent order to prevent deadlocks
- Appropriate for a financial application prioritizing data integrity

#### Optimistic Locking (Alternative Approach)

**Pros**:
- Higher throughput potential in low-contention scenarios
- No database locks held during the transaction
- Reduced resource utilization
- No deadlock potential

**Cons**:
- Requires retry logic to handle conflicts
- May lead to starvation under high contention
- More complex error handling for conflict resolution
- Can create a poor user experience with repeated retries

**How it would be implemented**:
- Add a `@Version` field to the Account entity
- Use standard JPA operations which automatically check version
- Implement retry logic when OptimisticLockExceptions occur
- Better suited for read-heavy workloads with infrequent updates

### TransactionTemplate vs @Transactional Annotation

#### TransactionTemplate (Current Implementation)

**Pros**:
- More explicit and fine-grained control over transaction boundaries
- Allows for different transaction settings within a single method
- Makes transaction scope visually clearer in the code
- Can potentially reduce lock holding time by minimizing transaction scope

**Cons**:
- More verbose code compared to annotations
- Requires explicit template creation and configuration
- May lead to boilerplate code in complex applications

**Implementation in our application**:
```java
return getTransactionTemplate().execute(status -> {
    // Execute transaction logic here
    executeTransaction(transaction);
    return transactionRepository.save(transaction);
});
```

#### @Transactional Annotation

**Pros**:
- Cleaner, more declarative code
- Automatic management of transaction boundaries
- Consistent behavior across all annotated methods
- Less boilerplate code

**Cons**:
- Less explicit control over transaction boundaries
- Transaction scope may be larger than necessary
- May hold locks longer than needed, reducing concurrency
- Class-level annotations can lead to unexpected transaction behavior

**How it would be implemented**:
```java
@Transactional
public Transaction transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
    // Transaction logic here
}
```

## Thread Safety Guarantees

The Cubeia Wallet application ensures thread safety through several complementary mechanisms:

1. **Deadlock Prevention with Consistent Lock Ordering**:
   - Accounts are always locked in a consistent order based on their IDs
   - If `fromAccountId <= toAccountId`, locks are acquired in the natural order (from → to)
   - If `fromAccountId > toAccountId`, locks are acquired in the reversed order (to → first)
   - This approach guarantees that concurrent transfers cannot deadlock

2. **Database-Level Pessimistic Locking**:
   - The `findByIdWithLock` method uses `LockModeType.PESSIMISTIC_WRITE`
   - This acquires exclusive database locks, preventing concurrent modifications
   - Locks are held until the transaction commits, ensuring atomicity

3. **Transaction Isolation**:
   - `READ_COMMITTED` isolation level prevents dirty reads
   - Combined with pessimistic locking, this ensures consistency
   - Transactions are managed using `TransactionTemplate` with properly defined boundaries

4. **Pre-Validation for Early Failure**:
   - Validation happens before acquiring expensive locks where possible
   - This reduces lock contention and improves throughput
   - The `ValidationService` centralizes this logic for consistency

5. **Atomic Operations**:
   - Account balance updates and transaction records are created in a single atomic transaction
   - Either all changes succeed or all fail and roll back
   - This maintains financial double-entry integrity

These mechanisms together ensure that:
- Account balances are always correct
- Money is neither created nor destroyed
- Concurrent transfers complete without conflicts
- The system remains responsive under high concurrency

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