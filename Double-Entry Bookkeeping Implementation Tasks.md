# Double-Entry Bookkeeping Implementation Tasks

This task list outlines the steps to enhance the Cubeia Wallet application by implementing a double-entry bookkeeping system. Each task follows a test-first approach and focuses on maintaining the core requirements of correctness, thread safety, and readability.

## Task Workflow for Every Item
1. **First, write or update tests** for the specific functionality
2. **Then, implement the necessary changes** to satisfy the tests
3. **Run the tests** and verify they pass
4. **If tests fail, analyze errors** and fix implementation until all tests pass
5. **Only then mark the task as complete** and move to the next task

## Task List

### 1. Create Ledger Entry Model
- [x] **Step 1:** Create `src/test/java/com/cubeia/wallet/model/LedgerEntryTest.java` with tests that:
  - Verify a LedgerEntry can be persisted with JPA EntityManager
  - Test that all properties are correctly maintained after persistence
  - Validate that required fields cannot be null
  - Test that entry types (DEBIT/CREDIT) work correctly
- [x] **Step 2:** Implement `LedgerEntry.java`:
  - Create an immutable entity with required fields: id, accountId, transactionId, entryType, amount, timestamp, description
  - Use appropriate JPA annotations for entity mapping
  - Include an enum for EntryType (DEBIT, CREDIT)
  - Ensure all necessary validation is in place
- [x] **Step 3:** Run the tests and verify JPA compatibility
- [x] **Step 4:** Fix any failing tests until all pass
- **Files**: `model/LedgerEntry.java`, `model/EntryType.java`, `model/LedgerEntryTest.java`

### 2. Create LedgerEntryRepository
- [x] **Step 1:** Create `src/test/java/com/cubeia/wallet/repository/LedgerEntryRepositoryTest.java` with tests that:
  - Verify finding entries by accountId works correctly
  - Test sum calculation methods function properly
  - Validate finding entries by transactionId returns correct results
  - Test pagination and sorting functionality
- [x] **Step 2:** Implement `LedgerEntryRepository.java` with methods:
  - `findByAccountIdOrderByTimestampDesc(UUID accountId, Pageable pageable)`
  - `findByTransactionId(UUID transactionId)`
  - `BigDecimal sumByAccountIdAndType(UUID accountId, EntryType type)`
  - Other necessary query methods
- [x] **Step 3:** Run tests and verify repository functionality
- [x] **Step 4:** Fix any failing tests until all pass
- **Files**: `repository/LedgerEntryRepository.java`, `repository/LedgerEntryRepositoryTest.java`

### 3. Implement DoubleEntryService
- [x] **Step 1:** Create `src/test/java/com/cubeia/wallet/service/DoubleEntryServiceTest.java` with tests for:
  - Creating balanced entry pairs for transfers
  - Balance calculation based on ledger entries
  - Error handling for unbalanced operations
  - Thread safety in concurrent operations
- [x] **Step 2:** Implement `DoubleEntryService.java` with methods:
  - `createTransferEntries(Transaction transaction)` to create balanced DEBIT/CREDIT pairs
  - `calculateBalance(UUID accountId)` to calculate balance from entries
  - `verifyBalance(UUID accountId, BigDecimal expectedBalance)` for validation
- [x] **Step 3:** Run tests and verify service functionality
- [x] **Step 4:** Fix any failing tests until all pass
- **Files**: `service/DoubleEntryService.java`, `service/DoubleEntryServiceTest.java`

### 4. Update TransactionService to use Double-Entry
- [ ] **Step 1:** Update `TransactionServiceTest.java` to verify:
  - Transfers create proper ledger entries
  - Balances remain correct after transfers
  - Transaction validation still works properly
  - Existing test cases pass with the new implementation
- [ ] **Step 2:** Modify `TransactionService.java` to:
  - Inject and use the new DoubleEntryService
  - Replace direct balance updates with ledger entry creation
  - Calculate balances from ledger entries instead of using stored balances
  - Maintain thread safety and transaction boundaries
- [ ] **Step 3:** Run tests and verify service functionality
- [ ] **Step 4:** Fix any failing tests until all pass
- **Files**: `service/TransactionService.java`, `service/TransactionServiceTest.java`

### 5. Update Account Model
- [ ] **Step 1:** Update `AccountTest.java` to verify:
  - Account no longer directly stores balance
  - `getBalance()` calculates from ledger entries
  - Account creation and persistence still work
- [ ] **Step 2:** Modify `Account.java` to:
  - Remove the `balance` field
  - Update `getBalance()` to delegate to a service
  - Remove `updateBalance()` method
  - Ensure compatibility with existing code
- [ ] **Step 3:** Run tests and verify entity functionality
- [ ] **Step 4:** Fix any failing tests until all pass
- **Files**: `model/Account.java`, `model/AccountTest.java`

### 6. Update API Endpoints and Controllers
- [ ] **Step 1:** Update `WalletControllerTest.java` to verify:
  - Balance API returns correct values from ledger calculations
  - Transfer API creates appropriate ledger entries
  - Transaction history includes relevant entry information
- [ ] **Step 2:** Modify `WalletController.java` to:
  - Update balance endpoint to use ledger-based calculation
  - Ensure transfer endpoint creates proper double-entry records
  - Add endpoint for viewing ledger entries directly if needed
- [ ] **Step 3:** Update DTOs as needed for the new model
- [ ] **Step 4:** Run tests and verify API functionality
- [ ] **Step 5:** Fix any failing tests until all pass
- **Files**: `controller/WalletController.java`, `controller/WalletControllerTest.java`, `dto/*.java`

### 7. Add Comprehensive Double-Entry Tests
- [ ] **Step 1:** Create `src/test/java/com/cubeia/wallet/DoubleEntryIntegrityTest.java` with tests that:
  - Verify money is neither created nor destroyed in transfer operations
  - Test that all accounts have balanced ledger entries
  - Validate integrity under concurrent operations
  - Test system-wide balance reconciliation
- [ ] **Step 2:** Create specialized test scenarios:
  - Chained transfers across multiple accounts
  - Concurrent transfers with shared accounts
  - High-volume transaction processing
- [ ] **Step 3:** Run tests and verify system integrity
- [ ] **Step 4:** Fix any issues discovered in testing
- **Files**: `test/java/com/cubeia/wallet/DoubleEntryIntegrityTest.java`

### 8. Update Concurrency and Performance Tests
- [ ] **Step 1:** Update `ConcurrentTransferTest.java` to:
  - Verify thread safety with the double-entry model
  - Test concurrent operations on shared accounts
  - Validate no race conditions exist in the new implementation
- [ ] **Step 2:** Update `PerformanceTest.java` to:
  - Benchmark performance with double-entry bookkeeping
  - Compare with previous implementation if possible
  - Test scaling with increasing transaction volume
- [ ] **Step 3:** Run tests and analyze results
- [ ] **Step 4:** Optimize if necessary based on performance findings
- **Files**: `test/java/com/cubeia/wallet/ConcurrentTransferTest.java`, `test/java/com/cubeia/wallet/PerformanceTest.java`

### 9. Update Documentation
- [ ] **Step 1:** Update `README.md` to include:
  - Explanation of double-entry bookkeeping implementation
  - Benefits of the new approach
  - Performance considerations section noting:
    * Memory usage implications of storing all ledger entries
    * Potential indexing requirements for efficient balance calculation
    * Caching strategies for frequently accessed balances
    * Potential for read-heavy query optimization
  - Updated API documentation
- [ ] **Step 2:** Create or update `IMPROVEMENTS.md` to document:
  - The reasoning for implementing double-entry bookkeeping
  - Technical benefits of the approach
  - Future enhancement possibilities
- [ ] **Step 3:** Add appropriate class-level documentation
- **Files**: `README.md`, `IMPROVEMENTS.md`, Various source files for Javadoc

### 10. Final Integration Testing
- [ ] **Step 1:** Create comprehensive end-to-end tests:
  - Test all API endpoints using the new implementation
  - Verify existing functionality works as expected
  - Validate new double-entry specific features
- [ ] **Step 2:** Run a full regression test suite
- [ ] **Step 3:** Fix any remaining issues
- [ ] **Step 4:** Verify documentation is complete and accurate
- **Files**: Various test files 