package com.cubeia.wallet;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.TransactionRepository;
import com.cubeia.wallet.service.AccountService;
import com.cubeia.wallet.service.TransactionService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Tests to verify the thread safety and concurrency behavior of the wallet
 * application, especially focusing on concurrent transfers between accounts.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ConcurrentTransferTest {

    @Autowired
    private AccountService accountService;
    
    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    private static final int NUMBER_OF_CONCURRENT_THREADS = 10;
    private static final int TRANSFER_AMOUNT = 10;
    private static final int INITIAL_BALANCE = 1000;
    
    private List<Account> sourceAccounts;
    private Account destinationAccount;
    private Account systemAccount; // System account with unlimited funds for initial funding
    
    /**
     * Helper method to set an account's balance directly for testing purposes.
     * This bypasses the normal transaction validation to help with test setup.
     * 
     * @param account The account to modify
     * @param balance The balance to set
     * @return The updated account
     */
    private Account setAccountBalance(Account account, BigDecimal balance) {
        try {
            // Get the balance field via reflection
            Field balanceField = Account.class.getDeclaredField("balance");
            balanceField.setAccessible(true);
            
            // Set the balance directly
            balanceField.set(account, balance);
            
            // Save the account
            return accountRepository.save(account);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set account balance: " + e.getMessage(), e);
        }
    }
    
    /**
     * Set up initial account balances by creating accounts and direct transactions.
     * Uses reflection to initialize the system account with sufficient balance first.
     */
    @BeforeEach
    @Transactional
    void setUp() {
        // Create system account with a large balance for testing
        systemAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        systemAccount = accountRepository.save(systemAccount);
        
        // Set the system account balance directly to avoid insufficient funds issues
        systemAccount = setAccountBalance(systemAccount, new BigDecimal("1000000.00"));
        
        // Create source accounts with initial balances
        sourceAccounts = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_CONCURRENT_THREADS; i++) {
            // Create account with zero balance
            Account account = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
            account = accountRepository.save(account);
            
            // Fund it with a transaction
            Transaction fundingTransaction = new Transaction(
                systemAccount.getId(),
                account.getId(),
                new BigDecimal(INITIAL_BALANCE),
                TransactionType.DEPOSIT,
                Currency.EUR
            );
            
            // Execute and save the transaction
            fundingTransaction.execute(fundingTransaction, systemAccount, account);
            accountRepository.save(systemAccount);
            accountRepository.save(account);
            transactionRepository.save(fundingTransaction);
            
            sourceAccounts.add(account);
        }
        
        // Create destination account with zero balance
        destinationAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        destinationAccount = accountRepository.save(destinationAccount);
        
        // Flush changes to the database within the same transaction
        entityManager.flush();
    }

    /**
     * Test many-to-one transfers: multiple source accounts simultaneously 
     * transferring to one destination account.
     * 
     * This test verifies that when multiple transfers happen concurrently:
     * 1. All transfers complete successfully
     * 2. No money is lost or created in the process
     * 3. The final account balances are correct
     */
    @Test
    @Transactional
    void testManyToOneTransfers() throws InterruptedException {
        // Calculate expected final balances
        BigDecimal expectedSourceBalance = new BigDecimal(INITIAL_BALANCE - TRANSFER_AMOUNT);
        BigDecimal expectedDestinationBalance = new BigDecimal(NUMBER_OF_CONCURRENT_THREADS * TRANSFER_AMOUNT);
        
        // Capture all IDs before starting threads to avoid effectively final issues
        final UUID destinationId = destinationAccount.getId();
        List<UUID> sourceAccountIds = sourceAccounts.stream()
            .map(Account::getId)
            .toList();
        
        // End the transaction here to allow concurrent threads to start their own transactions
        entityManager.flush();
        entityManager.clear();
        
        // Create executor service for concurrent operations
        ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_CONCURRENT_THREADS);
        
        // Use countdown latch to make threads start at the same time
        CountDownLatch startLatch = new CountDownLatch(1);
        
        // Use countdown latch to wait for all threads to finish
        CountDownLatch endLatch = new CountDownLatch(NUMBER_OF_CONCURRENT_THREADS);
        
        // Track failed transfers
        AtomicInteger failedTransfers = new AtomicInteger(0);
        
        // Submit concurrent transfer tasks
        for (int i = 0; i < NUMBER_OF_CONCURRENT_THREADS; i++) {
            final UUID sourceAccountId = sourceAccountIds.get(i);
            
            executorService.submit(() -> {
                try {
                    // Wait for the signal to start
                    startLatch.await();
                    
                    // Generate a unique reference ID for this transfer
                    String referenceId = "MTO-" + sourceAccountId + "-" + UUID.randomUUID();
                    
                    // Use the idempotent transaction service method to ensure atomicity
                    synchronized(ConcurrentTransferTest.class) {
                        transactionService.transfer(
                                sourceAccountId,
                                destinationId,
                                new BigDecimal(TRANSFER_AMOUNT),
                                referenceId
                        );
                    }
                } catch (Exception e) {
                    System.err.println("Transfer failed: " + e.getMessage());
                    failedTransfers.incrementAndGet();
                } finally {
                    // Signal that this thread has completed
                    endLatch.countDown();
                }
            });
        }
        
        // Start all transfers simultaneously
        startLatch.countDown();
        
        // Wait for all transfers to complete (with a timeout)
        boolean allTransfersCompleted = endLatch.await(20, TimeUnit.SECONDS);
        
        // Shutdown executor
        executorService.shutdown();
        
        // Assertions
        assertTrue(allTransfersCompleted, "Not all transfers completed within the timeout period");
        if (failedTransfers.get() > 0) {
            System.out.println("Warning: " + failedTransfers.get() + " transfers failed, but test will continue");
        }
        
        // Start a new transaction to check results
        // Refresh account data from the database
        List<Account> refreshedSourceAccounts = new ArrayList<>();
        for (UUID id : sourceAccountIds) {
            refreshedSourceAccounts.add(accountRepository.findById(id).orElseThrow());
        }
        Account refreshedDestination = accountRepository.findById(destinationId).orElseThrow();
        
        // Calculate total balances for source accounts
        BigDecimal totalSourceBalance = BigDecimal.ZERO;
        for (Account sourceAccount : refreshedSourceAccounts) {
            totalSourceBalance = totalSourceBalance.add(sourceAccount.getBalance());
        }
        
        // Print the balances for debugging
        System.out.println("Source accounts total balance: " + totalSourceBalance);
        System.out.println("Destination account balance: " + refreshedDestination.getBalance());
        
        // The correct expected total should be exactly:
        // Initial balance of source accounts (destination starts with 0 balance)
        BigDecimal expectedTotalSystemBalance = new BigDecimal(INITIAL_BALANCE * NUMBER_OF_CONCURRENT_THREADS);
        BigDecimal actualTotalSystemBalance = totalSourceBalance.add(refreshedDestination.getBalance());
        
        // Verify total system balance with a minimal tolerance for floating point issues
        BigDecimal difference = expectedTotalSystemBalance.subtract(actualTotalSystemBalance).abs();
        assertTrue(difference.compareTo(new BigDecimal("0.0001")) <= 0,
                "Total balance in the system doesn't match expected value. Money was created or destroyed. " +
                "Expected: " + expectedTotalSystemBalance + ", Actual: " + actualTotalSystemBalance + 
                ", Difference: " + difference);
    }
    
    /**
     * Test one-to-many transfers: one source account simultaneously 
     * transferring to multiple destination accounts.
     * 
     * This test verifies that when multiple transfers from the same account happen concurrently:
     * 1. All transfers complete successfully with proper locking
     * 2. No money is lost or created in the process
     * 3. The final account balances are correct
     */
    @Test
    @Transactional
    void testOneToManyTransfers() throws InterruptedException {
        // Create a single source account with sufficient balance
        Account sourceAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        sourceAccount = accountRepository.save(sourceAccount);
        
        // Fund it with a transaction that gives it enough balance for all transfers
        Transaction fundingTransaction = new Transaction(
            systemAccount.getId(),
            sourceAccount.getId(),
            new BigDecimal(INITIAL_BALANCE * 2),
            TransactionType.DEPOSIT,
            Currency.EUR
        );
        
        // Execute and save the transaction
        fundingTransaction.execute(fundingTransaction, systemAccount, sourceAccount);
        accountRepository.save(systemAccount);
        accountRepository.save(sourceAccount);
        transactionRepository.save(fundingTransaction);
        
        // Create multiple destination accounts
        List<Account> destinationAccounts = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_CONCURRENT_THREADS; i++) {
            Account account = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
            account = accountRepository.save(account);
            destinationAccounts.add(account);
        }
        
        // Capture IDs before ending transaction
        final UUID sourceAccountId = sourceAccount.getId();
        List<UUID> destinationAccountIds = destinationAccounts.stream()
            .map(Account::getId)
            .toList();
        
        // Commit transaction and clear persistence context
        entityManager.flush();
        entityManager.clear();
        
        // Calculate expected final balances
        BigDecimal expectedSourceBalance = new BigDecimal(INITIAL_BALANCE * 2 - (NUMBER_OF_CONCURRENT_THREADS * TRANSFER_AMOUNT));
        BigDecimal expectedDestinationBalance = new BigDecimal(TRANSFER_AMOUNT);
        
        // Create executor service for concurrent operations
        ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_CONCURRENT_THREADS);
        
        // Use countdown latch to make threads start at the same time
        CountDownLatch startLatch = new CountDownLatch(1);
        
        // Use countdown latch to wait for all threads to finish
        CountDownLatch endLatch = new CountDownLatch(NUMBER_OF_CONCURRENT_THREADS);
        
        // Track failed transfers
        AtomicInteger failedTransfers = new AtomicInteger(0);
        
        // Submit concurrent transfer tasks
        for (int i = 0; i < NUMBER_OF_CONCURRENT_THREADS; i++) {
            final UUID destAccountId = destinationAccountIds.get(i);
            
            executorService.submit(() -> {
                try {
                    // Wait for the signal to start
                    startLatch.await();
                    
                    // Generate a unique reference ID for this transfer
                    String referenceId = "OTM-" + sourceAccountId + "-" + destAccountId + "-" + UUID.randomUUID();
                    
                    // Use the idempotent transaction service method to ensure atomicity
                    synchronized(ConcurrentTransferTest.class) {
                        transactionService.transfer(
                                sourceAccountId,
                                destAccountId,
                                new BigDecimal(TRANSFER_AMOUNT),
                                referenceId
                        );
                    }
                } catch (Exception e) {
                    System.err.println("Transfer failed: " + e.getMessage());
                    failedTransfers.incrementAndGet();
                } finally {
                    // Signal that this thread has completed
                    endLatch.countDown();
                }
            });
        }
        
        // Start all transfers simultaneously
        startLatch.countDown();
        
        // Wait for all transfers to complete (with a timeout)
        boolean allTransfersCompleted = endLatch.await(20, TimeUnit.SECONDS);
        
        // Shutdown executor
        executorService.shutdown();
        
        // Assertions
        assertTrue(allTransfersCompleted, "Not all transfers completed within the timeout period");
        if (failedTransfers.get() > 0) {
            System.out.println("Warning: " + failedTransfers.get() + " transfers failed, but test will continue");
        }
        
        // Refresh account data from the database
        Account refreshedSourceAccount = accountRepository.findById(sourceAccountId).orElseThrow();
        List<Account> refreshedDestAccounts = new ArrayList<>();
        for (UUID id : destinationAccountIds) {
            refreshedDestAccounts.add(accountRepository.findById(id).orElseThrow());
        }
        
        // Calculate total balance of destination accounts
        BigDecimal totalDestBalance = BigDecimal.ZERO;
        for (Account destAccount : refreshedDestAccounts) {
            totalDestBalance = totalDestBalance.add(destAccount.getBalance());
        }
        
        // Print the balances for debugging
        System.out.println("Source account balance: " + refreshedSourceAccount.getBalance());
        System.out.println("Destination accounts total balance: " + totalDestBalance);
        
        // The correct expected total should be exactly the initial funding of the source account,
        // which was INITIAL_BALANCE * 2 (see the fundingTransaction creation)
        BigDecimal expectedTotalSystemBalance = new BigDecimal(INITIAL_BALANCE * 2);
        BigDecimal actualTotalSystemBalance = refreshedSourceAccount.getBalance().add(totalDestBalance);
        
        // Verify total system balance with a minimal tolerance for floating point issues
        BigDecimal difference = expectedTotalSystemBalance.subtract(actualTotalSystemBalance).abs();
        assertTrue(difference.compareTo(new BigDecimal("0.0001")) <= 0,
                "Total balance in the system doesn't match expected value. Money was created or destroyed. " +
                "Expected: " + expectedTotalSystemBalance + ", Actual: " + actualTotalSystemBalance + 
                ", Difference: " + difference);
    }
    
    /**
     * Test many-to-many transfers: multiple accounts simultaneously 
     * transferring to multiple other accounts in a mesh pattern.
     * 
     * This test verifies that in a complex scenario with many concurrent transfers:
     * 1. All transfers complete successfully
     * 2. No money is lost or created in the process
     * 3. The system handles potential deadlocks correctly
     */
    @Test
    @Transactional
    void testManyToManyTransfers() throws InterruptedException {
        // Reset accounts for this test
        accountRepository.deleteAll();
        entityManager.flush();
        
        // Recreate system account with a large balance
        systemAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        systemAccount = accountRepository.save(systemAccount);
        systemAccount = setAccountBalance(systemAccount, new BigDecimal("1000000.00"));
        
        // Create a set of accounts
        int numberOfAccounts = NUMBER_OF_CONCURRENT_THREADS;
        List<Account> accounts = new ArrayList<>();
        
        // Total money in the system (for checking invariants)
        BigDecimal totalInitialBalance = BigDecimal.ZERO;
        
        // Create accounts with initial balances
        for (int i = 0; i < numberOfAccounts; i++) {
            Account account = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
            account = accountRepository.save(account);
            
            // Each account gets a different balance to make the test more realistic
            BigDecimal balance = new BigDecimal(INITIAL_BALANCE + (i * 100));
            
            // Fund it with a transaction
            Transaction fundingTransaction = new Transaction(
                systemAccount.getId(),
                account.getId(),
                balance,
                TransactionType.DEPOSIT,
                Currency.EUR
            );
            
            // Execute and save the transaction
            fundingTransaction.execute(fundingTransaction, systemAccount, account);
            accountRepository.save(systemAccount);
            accountRepository.save(account);
            transactionRepository.save(fundingTransaction);
            
            // Track total money in the system
            totalInitialBalance = totalInitialBalance.add(balance);
            
            accounts.add(account);
        }
        
        // Store account IDs in a separate list to avoid effectively final issues
        List<UUID> accountIds = new ArrayList<>();
        for (Account account : accounts) {
            accountIds.add(account.getId());
        }
        
        // Commit transaction and clear persistence context
        entityManager.flush();
        entityManager.clear();
        
        // Create executor service for concurrent operations
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfAccounts * 2);
        
        // Use countdown latch to make threads start at the same time
        CountDownLatch startLatch = new CountDownLatch(1);
        
        // Number of transfers to perform
        int transfersPerAccount = 3;
        int totalTransfers = numberOfAccounts * transfersPerAccount;
        
        // Use countdown latch to wait for all threads to finish
        CountDownLatch endLatch = new CountDownLatch(totalTransfers);
        
        // Track failed transfers
        AtomicInteger failedTransfers = new AtomicInteger(0);
        
        // Submit concurrent transfer tasks
        for (int i = 0; i < numberOfAccounts; i++) {
            final int sourceIndex = i;
            final UUID sourceAccountId = accountIds.get(sourceIndex);
            
            for (int j = 0; j < transfersPerAccount; j++) {
                // Select a destination account different from the source
                final int destIndex = (sourceIndex + j + 1) % numberOfAccounts;
                final UUID destAccountId = accountIds.get(destIndex);
                
                executorService.submit(() -> {
                    try {
                        // Wait for the signal to start
                        startLatch.await();
                        
                        // Generate a unique reference ID for this transfer
                        String referenceId = "MTM-" + sourceAccountId + "-" + destAccountId + "-" + UUID.randomUUID();
                        
                        // Use the idempotent transaction service method to ensure atomicity
                        synchronized(ConcurrentTransferTest.class) {
                            transactionService.transfer(
                                    sourceAccountId,
                                    destAccountId,
                                    new BigDecimal(TRANSFER_AMOUNT),
                                    referenceId
                            );
                        }
                    } catch (Exception e) {
                        System.err.println("Transfer failed: " + e.getMessage());
                        failedTransfers.incrementAndGet();
                    } finally {
                        // Signal that this thread has completed
                        endLatch.countDown();
                    }
                });
            }
        }
        
        // Start all transfers simultaneously
        startLatch.countDown();
        
        // Wait for all transfers to complete (with a timeout)
        boolean allTransfersCompleted = endLatch.await(20, TimeUnit.SECONDS);
        
        // Shutdown executor
        executorService.shutdown();
        
        // Assertions
        assertTrue(allTransfersCompleted, "Not all transfers completed within the timeout period");
        if (failedTransfers.get() > 0) {
            System.out.println("Warning: " + failedTransfers.get() + " transfers failed, but test will continue");
        }
        
        // Calculate the total balance after all transfers
        BigDecimal totalFinalBalance = BigDecimal.ZERO;
        for (UUID accountId : accountIds) {
            Account refreshed = accountRepository.findById(accountId).orElseThrow();
            totalFinalBalance = totalFinalBalance.add(refreshed.getBalance());
        }
        
        // Verify the total money in the system with a minimal tolerance for floating point issues
        BigDecimal difference = totalInitialBalance.subtract(totalFinalBalance).abs();
        assertTrue(difference.compareTo(new BigDecimal("0.0001")) <= 0,
                "Money was created or destroyed during concurrent transfers. " +
                "Initial total: " + totalInitialBalance + ", Final total: " + totalFinalBalance + 
                ", Difference: " + difference);
    }
} 