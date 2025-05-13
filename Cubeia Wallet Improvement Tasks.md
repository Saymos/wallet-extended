# Cubeia Wallet Improvement Tasks

This task list outlines the steps to improve the Cubeia Wallet application based on code review analysis. Each task follows a strict test-first approach and focuses on enhancing specific aspects while maintaining the core requirements of correctness, thread safety, and readability.

## Task Workflow for Every Item
1. **First, write or update tests** for the specific functionality
2. **Then, implement the necessary changes** to satisfy the tests
3. **Run the tests** and verify they pass
4. **If tests fail, analyze errors** and fix implementation until all tests pass
5. **Only then mark the task as complete** and move to the next task

## Task List

### 1. Improve Entity JPA Compatibility
- [x] **Step 1:** Create `src/test/java/com/cubeia/wallet/model/AccountTest.java` with tests that:
  - Verify an Account can be persisted with JPA EntityManager (use TestEntityManager if using Spring Boot Test)
  - Verify retrieved Account objects have all properties correctly maintained
  - Specifically test that Account can be created by JPA and then loaded back with consistent state
- [x] **Step 2:** Modify `Account.java` and `Transaction.java` to:
  - Remove 'final' modifiers from fields `currency` and `accountType` in Account
  - Remove 'final' modifiers from entity fields in Transaction
  - Ensure proper JavaBean pattern with getters/setters while maintaining encapsulation
- [x] **Step 3:** Run the tests and verify JPA compatibility
- [x] **Step 4:** Fix any failing tests until all pass
- **Files**: `model/Account.java`, `model/Transaction.java`, `model/AccountTest.java`

### 2. Enhance Concurrency Tests
- [x] **Step 1:** Create `src/test/java/com/cubeia/wallet/ConcurrentTransferTest.java` with:
  - A setup method creating test accounts with known balances
  - A test for many-to-one transfers: 10+ accounts simultaneously transferring to one account
  - A test for one-to-many transfers: one account simultaneously transferring to 10+ accounts
  - A test for many-to-many transfers: multiple accounts transferring to multiple accounts simultaneously
  - Use of `ExecutorService` and `CountDownLatch` to coordinate concurrent execution
  - Assertions that final balance totals match expected values
- [x] **Step 2:** Add detailed comments to `TransactionService.java` explaining:
  - The deadlock prevention strategy using ordered locks
  - How the comparison of account IDs ensures consistent lock ordering
  - Why this approach prevents deadlocks during concurrent transfers
- [x] **Step 3:** Create focused `DeadlockPreventionTest.java` to specifically test deadlock prevention mechanism
  - Test includes concurrent transfers in opposite directions between the same accounts
  - Verifies the system correctly orders locks to prevent deadlocks
  - Confirms account balances remain correct after concurrent operations
- [x] **Step 4:** Verify that deadlock prevention works correctly with transaction boundaries
- **Files**: `test/java/com/cubeia/wallet/ConcurrentTransferTest.java`, `test/java/com/cubeia/wallet/DeadlockPreventionTest.java`, `service/TransactionService.java`

### 3. Add Idempotency Support
- [ ] **Step 1:** Create `src/test/java/com/cubeia/wallet/TransactionIdempotencyTest.java` with tests that:
  - Verify a transaction with a specific reference ID succeeds the first time
  - Verify the same transaction (same reference ID) returns the same result but doesn't duplicate the transfer
  - Test various edge cases (null reference, different amounts with same reference, etc.)
- [ ] **Step 2:** Update models and DTOs:
  - Add `referenceId` field (String or UUID) to `Transaction.java`
  - Add optional `referenceId` field to `TransferRequestDto.java` with appropriate validation
- [ ] **Step 3:** Update `TransactionRepository.java` to add:
  - `Optional<Transaction> findByReferenceId(String referenceId)` method
- [ ] **Step 4:** Modify `TransactionService.java` to:
  - Check for existing transaction with same reference ID before processing
  - Return existing transaction if found instead of creating a duplicate
  - Ensure consistent behavior for repeated requests
- [ ] **Step 5:** Update controller and other service tests as needed
- [ ] **Step 6:** Run all tests and fix any failures
- **Files**: `dto/TransferRequestDto.java`, `model/Transaction.java`, `repository/TransactionRepository.java`, `service/TransactionService.java`, `controller/WalletController.java`, `test/java/com/cubeia/wallet/TransactionIdempotencyTest.java`

### 4. Refine Transaction Management with TransactionTemplate
- [ ] **Step 1:** Update `src/test/java/com/cubeia/wallet/service/TransactionServiceTest.java` to:
  - Test successful transfers with more fine-grained transaction boundaries
  - Test transaction rollback scenarios (exceptions during balance update, etc.)
  - Verify transaction isolation behavior
- [ ] **Step 2:** Modify `TransactionService.java` to:
  - Inject `PlatformTransactionManager` and create a `TransactionTemplate` instance
  - Replace `@Transactional` with explicit `transactionTemplate.execute()` calls
  - Wrap only the critical section (rows ~92-111) that updates account balances and saves records
  - Add clear comments explaining the benefits of this approach
- [ ] **Step 3:** Run the tests and verify they pass
- [ ] **Step 4:** Fix any failing tests until all pass
- **Files**: `service/TransactionService.java`, `service/TransactionServiceTest.java`

### 5. Simplify Transaction Execution Model
- [ ] **Step 1:** Update `TransactionServiceTest.java` to verify:
  - Account balances are correctly updated during transfers
  - Transaction records are properly created
  - All validations still function correctly
  - The same level of thread safety is maintained
- [ ] **Step 2:** Modify `Transaction.java` to:
  - Remove the complex `execute` methods
  - Make it a simpler data container/record of transfers
- [ ] **Step 3:** Update `TransactionService.java` to:
  - Move all validation logic from Transaction to the service
  - Handle balance updates directly in the service method
  - Create and save Transaction records after successful balance updates
  - Maintain the same validations and business rules
- [ ] **Step 4:** Run all tests and fix any failures
- **Files**: `model/Transaction.java`, `service/TransactionService.java`, `service/TransactionServiceTest.java`

### 6. Simplify Account Type Handling
- [ ] **Step 1:** Add detailed comments to `AccountType.java` explaining:
  - The design decision to use different account types
  - How different account types would have different properties in production
  - Examples like bonus accounts with expiration dates, etc.
- [ ] **Step 2:** Simplify the implementation:
  - Keep the sealed interface approach
  - Reduce complexity of pattern matching in switch statements
  - Consider using more traditional methods instead of pattern matching where appropriate
- [ ] **Step 3:** Update affected tests to verify behavior remains correct
- [ ] **Step 4:** Run tests and fix any failures
- **Files**: `model/AccountType.java`, related test files

### 7. Improve Error Handling
- [ ] **Step 1:** Create specific exception classes:
  - `CurrencyMismatchException.java` for currency-related errors
  - `InvalidTransactionException.java` for general transaction validation errors
  - Other specific exceptions as needed
- [ ] **Step 2:** Write tests that verify each exception:
  - Produces the correct HTTP status code
  - Returns appropriate error messages
  - Is thrown in the right scenarios
- [ ] **Step 3:** Update `GlobalExceptionHandler.java` to:
  - Handle all custom exceptions with appropriate HTTP status codes
  - Return consistent error response format for all errors
  - Include helpful error messages for clients
- [ ] **Step 4:** Update service code to:
  - Throw the new specific exceptions instead of generic ones
  - Improve error messages to be more descriptive
- [ ] **Step 5:** Run tests and fix any failures
- **Files**: `exception/*.java`, `exception/GlobalExceptionHandler.java`, `controller/WalletControllerTest.java`, `service/TransactionService.java`

### 8. Clean Up Validation Logic
- [ ] **Step 1:** Write controller tests that:
  - Verify invalid inputs are rejected at the API boundary
  - Confirm proper status codes and error messages are returned
- [ ] **Step 2:** Identify and remove duplicate validations:
  - Keep validations at the controller/DTO level for API input validation
  - Retain only essential business rule validations in the service layer
  - Remove redundant parameter checks if already handled by DTOs
- [ ] **Step 3:** Run tests and verify all validations still work correctly
- [ ] **Step 4:** Fix any failing tests until all pass
- **Files**: `service/TransactionService.java`, `service/AccountService.java`, `controller/WalletController.java`

### 9. Update Documentation and README
- [ ] **Step 1:** Update `README.md` to include:
  - A comprehensive 'Production Considerations' section addressing:
    * Audit trail implementation for financial systems
    * Logging requirements for financial transactions
    * Performance considerations for high concurrency
    * Transaction isolation levels and their trade-offs
    * Clustering and horizontal scaling approaches
    * The use of virtual threads and their benefits
    * Security considerations for a financial API
  - Detailed pros/cons comparison of:
    * Pessimistic locking vs optimistic locking
    * TransactionTemplate vs @Transactional annotation
  - Clear explanation of how thread safety is guaranteed
  - Build and run instructions
  - API documentation
- [ ] **Step 2:** Review documentation for clarity and completeness
- **Files**: `README.md`

### 10. Final Code Review and Performance Testing
- [ ] **Step 1:** Create `src/test/java/com/cubeia/wallet/PerformanceTest.java`:
  - Set up test to create 100+ concurrent transfer requests
  - Use thread pools and latches to synchronize execution
  - Verify correct final balances and transaction records
  - Measure response times and throughput
- [ ] **Step 2:** Perform full code review checking:
  - Thread safety in all operations
  - Proper transaction boundaries
  - Clean separation of concerns
  - Consistency in error handling
  - Code simplicity and maintainability
- [ ] **Step 3:** Fix any issues found during review or performance testing
- [ ] **Step 4:** Document performance test results
- **Files**: All source files, new `test/java/com/cubeia/wallet/PerformanceTest.java`

### 11. Write Migration Documentation
- [ ] Create `IMPROVEMENTS.md` documenting for each improvement:
  - The original issue or limitation identified
  - The specific changes implemented to address it
  - Technical benefits of the new approach
  - Any trade-offs or considerations made
  - Potential future improvements beyond the scope of this exercise
- **Files**: `IMPROVEMENTS.md`