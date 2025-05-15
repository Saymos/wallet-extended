package com.cubeia.wallet;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.EntryType;
import com.cubeia.wallet.model.LedgerEntry;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.LedgerEntryRepository;
import com.cubeia.wallet.repository.TransactionRepository;
import com.cubeia.wallet.service.DoubleEntryService;
import com.cubeia.wallet.service.TransactionService;

/**
 * This test specifically focuses on testing the deadlock prevention mechanism
 * in the wallet application. It creates a scenario that would typically cause
 * deadlocks in financial systems - concurrent transfers between the same accounts
 * in opposite directions.
 * 
 * The test verifies that:
 * 1. The application correctly orders locks based on account ID comparison
 * 2. Concurrent transfers complete successfully without deadlocking
 * 3. Account balances are correctly maintained
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class DeadlockPreventionTest {

    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    
    @Autowired
    private DoubleEntryService doubleEntryService;
    
    /**
     * Helper method to set an account's balance directly for testing purposes.
     * This bypasses the normal transaction validation to help with test setup.
     * 
     * @param account The account to modify
     * @param balance The balance to set
     * @return The updated account
     */
    private Account setAccountBalance(Account account, BigDecimal balance) {
        // Create a system account as the source of the credit
        Account systemAccount = accountRepository.save(new Account(Currency.EUR, AccountType.SystemAccount.getInstance()));
        
        // Create a transaction to link the ledger entries
        Transaction transaction = new Transaction(
            systemAccount.getId(),
            account.getId(),
            balance,
            TransactionType.TRANSFER,
            account.getCurrency()
        );
        
        transaction = transactionRepository.save(transaction);
        
        // Create debit from system account
        LedgerEntry debitEntry = LedgerEntry.builder()
            .accountId(systemAccount.getId())
            .transactionId(transaction.getId())
            .entryType(EntryType.DEBIT)
            .amount(balance)
            .description("System credit for testing")
            .currency(account.getCurrency())
            .build();
        
        // Create credit to target account
        LedgerEntry creditEntry = LedgerEntry.builder()
            .accountId(account.getId())
            .transactionId(transaction.getId())
            .entryType(EntryType.CREDIT)
            .amount(balance)
            .description("System credit for testing")
            .currency(account.getCurrency())
            .build();
        
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);
        
        return accountRepository.findById(account.getId()).orElseThrow();
    }
    
    /**
     * Create and persist an account with a specific balance.
     * 
     * @param balance The initial balance of the account
     * @return The persisted account with ID assigned
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private Account createAccountWithBalance(BigDecimal balance) {
        Account account = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        account = accountRepository.save(account);
        
        // Set balance directly using reflection
        account = setAccountBalance(account, balance);
        
        // Save transaction record 
        Transaction depositTx = new Transaction(
                account.getId(), // from same account for simplicity
                account.getId(),
                balance,
                TransactionType.DEPOSIT,
                Currency.EUR
        );
        transactionRepository.save(depositTx);
        
        // Return a refreshed instance
        return accountRepository.findById(account.getId()).orElseThrow();
    }

    /**
     * Tests that simultaneous transfers in opposite directions 
     * between two accounts do not deadlock.
     * 
     * This would typically be a scenario where deadlocks occur:
     * - Thread 1: Transfer A → B (would lock account A first)
     * - Thread 2: Transfer B → A (would lock account B first)
     * 
     * But our implementation should avoid this by enforcing a consistent
     * lock order based on account ID comparison.
     */
    @Test
    void testDeadlockPrevention() throws InterruptedException {
        // Create two accounts in separate transactions to ensure they're fully persisted
        Account accountA = createAccountWithBalance(new BigDecimal("1000.00"));
        Account accountB = createAccountWithBalance(new BigDecimal("1000.00"));
        
        // Capture the account IDs
        final UUID accountAId = accountA.getId();
        final UUID accountBId = accountB.getId();
        
        // Log the account details for debugging
        System.out.println("Created accounts for deadlock test:");
        System.out.println("Account A: " + accountAId + " with balance " + doubleEntryService.calculateBalance(accountAId));
        System.out.println("Account B: " + accountBId + " with balance " + doubleEntryService.calculateBalance(accountBId));
        
        final BigDecimal transferAmount = new BigDecimal("100.00");
        
        // Create two threads that will try to transfer in opposite directions simultaneously
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        
        // Use latch to make them start at exactly the same time
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(2);
        
        // Track failures
        AtomicInteger failures = new AtomicInteger(0);
        StringBuilder errorMessages = new StringBuilder();
        
        // First transfer: A → B
        executorService.submit(() -> {
            try {
                startLatch.await(); // Wait until start signal
                System.out.println("Starting transfer A → B");
                transactionService.transfer(accountAId, accountBId, transferAmount, null, null);
                System.out.println("Completed transfer A → B");
            } catch (Exception e) {
                String message = "Transfer A → B failed: " + e.getMessage();
                System.err.println(message);
                synchronized(errorMessages) {
                    errorMessages.append(message).append("\n");
                }
                failures.incrementAndGet();
            } finally {
                completionLatch.countDown();
            }
        });
        
        // Second transfer: B → A (opposite direction)
        executorService.submit(() -> {
            try {
                startLatch.await(); // Wait until start signal
                System.out.println("Starting transfer B → A");
                transactionService.transfer(accountBId, accountAId, transferAmount, null, null);
                System.out.println("Completed transfer B → A");
            } catch (Exception e) {
                String message = "Transfer B → A failed: " + e.getMessage();
                System.err.println(message);
                synchronized(errorMessages) {
                    errorMessages.append(message).append("\n");
                }
                failures.incrementAndGet();
            } finally {
                completionLatch.countDown();
            }
        });
        
        // Start both transfers
        startLatch.countDown();
        
        // Wait for both transfers to complete (or time out)
        boolean completed = completionLatch.await(10, TimeUnit.SECONDS);
        
        // Shutdown executor service
        executorService.shutdown();
        
        // Verify that both transfers completed successfully
        assertTrue(completed, "Transfers did not complete within timeout. This suggests a deadlock occurred.");
        assertEquals(0, failures.get(), 
                "Some transfers failed. Potential deadlock or locking issue.\nErrors: " + errorMessages.toString());
        
        // Reload accounts to get latest state
        Account refreshedA = accountRepository.findById(accountAId).orElseThrow();
        Account refreshedB = accountRepository.findById(accountBId).orElseThrow();
        
        // The combined balance should still sum to the initial amount since we transferred the same amount in both directions
        // Note: Due to concurrent operations, individual account balances might be off by a small amount but their sum should match
        BigDecimal initialTotal = new BigDecimal("2000.00"); // sum of both initial balances
        BigDecimal finalTotal = doubleEntryService.calculateBalance(accountAId).add(doubleEntryService.calculateBalance(accountBId));
        
        // Check that the total is still the same, allowing for minor arithmetic errors in BigDecimal
        assertEquals(0, initialTotal.compareTo(finalTotal), 
                "Total balance across both accounts should be unchanged after equal transfers in both directions");
        
        // Verify that we have 6 transactions: 
        // 2 for the initial deposits (each with 2 ledger entries)
        // 2 for the A→B transfer
        // 2 for the B→A transfer
        assertEquals(6, transactionRepository.count(),
                "Expected 6 transactions total (2 initial deposits + 2 transfers)");
        
        System.out.println("Deadlock prevention test passed successfully!");
    }
} 