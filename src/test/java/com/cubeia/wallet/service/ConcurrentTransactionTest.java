package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.repository.AccountRepository;

/**
 * Integration tests for checking deadlock prevention in concurrent transactions.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ConcurrentTransactionTest {

    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private DoubleEntryService doubleEntryService;
    
    @Autowired
    private TransactionTemplate transactionTemplate;
    
    /**
     * Helper method to create a system credit for an account (for testing)
     * Also ensures the account has DoubleEntryService properly injected
     */
    private void createSystemCredit(Account account, BigDecimal amount) {
        // No more setDoubleEntryService
        transactionTemplate.execute(status -> {
            doubleEntryService.createSystemCreditEntry(account.getId(), amount, "System credit for testing");
            return doubleEntryService.calculateBalance(account.getId());
        });
    }
    
    /**
     * Helper method to create and prepare an account with proper service injection
     */
    private Account createAndPrepareAccount(Currency currency, AccountType accountType) {
        Account account = new Account(currency, accountType);
        account = accountRepository.save(account);
        // No more setDoubleEntryService
        return account;
    }
    
    /**
     * Test for concurrent transactions in opposite directions.
     * This test should detect potential deadlocks when transferring between
     * two accounts simultaneously in opposite directions.
     */
    @Test
    public void testConcurrentOppositeTransfers() {
        // Given - Create two accounts with initial balances
        Account account1 = createAndPrepareAccount(Currency.EUR, AccountType.MainAccount.getInstance());
        Account account2 = createAndPrepareAccount(Currency.EUR, AccountType.MainAccount.getInstance());
        
        createSystemCredit(account1, new BigDecimal("1000.00"));
        createSystemCredit(account2, new BigDecimal("1000.00"));
        
        // Set up thread synchronization
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicReference<Exception> exception = new AtomicReference<>();
        
        // Should complete within this timeout if no deadlock
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            // Create executor service for running concurrent threads
            ExecutorService executor = Executors.newFixedThreadPool(2);
            
            // First thread: A → B
            Future<?> task1 = executor.submit(() -> {
                try {
                    await(startLatch);
                    transactionService.transfer(account1.getId(), account2.getId(), new BigDecimal("100.00"));
                } catch (Exception e) {
                    exception.set(e);
                }
            });
            
            // Second thread: B → A
            Future<?> task2 = executor.submit(() -> {
                try {
                    await(startLatch);
                    transactionService.transfer(account2.getId(), account1.getId(), new BigDecimal("50.00"));
                } catch (Exception e) {
                    exception.set(e);
                }
            });
            
            // Start both threads simultaneously
            startLatch.countDown();
            
            // Wait for completion
            executor.shutdown();
            boolean completed = executor.awaitTermination(5, TimeUnit.SECONDS);
            
            // Check for exceptions
            if (exception.get() != null) {
                fail("Exception during concurrent transfers: " + exception.get().getMessage());
            }
            
            // Should have completed (if not, likely a deadlock)
            if (!completed) {
                executor.shutdownNow();
                fail("Potential deadlock detected - transfers did not complete");
            }
            
            // Verify the final balances using DoubleEntryService
            BigDecimal finalBalance1 = doubleEntryService.calculateBalance(account1.getId());
            BigDecimal finalBalance2 = doubleEntryService.calculateBalance(account2.getId());
            
            // A1 started with 1000, sent 100, received 50 = 950
            // A2 started with 1000, sent 50, received 100 = 1050
            assertEquals(0, new BigDecimal("950.00").compareTo(finalBalance1),
                "Account 1 should have balance of 950.00");
            assertEquals(0, new BigDecimal("1050.00").compareTo(finalBalance2),
                "Account 2 should have balance of 1050.00");
        });
    }
    
    /**
     * Test for many concurrent transfers to the same account from multiple accounts.
     * This tests for potential deadlocks or lost updates with high concurrency.
     */
    @Test
    public void testManyConcurrentTransfersToSameAccount() {
        int numSenders = 10;
        BigDecimal transferAmount = new BigDecimal("10.00");
        
        // Create a destination account
        Account destinationAccount = createAndPrepareAccount(Currency.EUR, AccountType.MainAccount.getInstance());
        
        // Create multiple sender accounts
        List<Account> senderAccounts = new ArrayList<>();
        for (int i = 0; i < numSenders; i++) {
            Account sender = createAndPrepareAccount(Currency.EUR, AccountType.MainAccount.getInstance());
            createSystemCredit(sender, new BigDecimal("100.00"));
            senderAccounts.add(sender);
        }
        
        // Set up thread synchronization
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicReference<Exception> exception = new AtomicReference<>();
        
        // Should complete within this timeout if no deadlock
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            // Create executor service for running concurrent threads
            ExecutorService executor = Executors.newFixedThreadPool(numSenders);
            List<Future<?>> tasks = new ArrayList<>();
            
            // Submit transfer tasks
            for (Account sender : senderAccounts) {
                Future<?> task = executor.submit(() -> {
                    try {
                        await(startLatch);
                        transactionService.transfer(
                            sender.getId(), 
                            destinationAccount.getId(), 
                            transferAmount
                        );
                    } catch (Exception e) {
                        exception.set(e);
                    }
                });
                tasks.add(task);
            }
            
            // Start all threads simultaneously
            startLatch.countDown();
            
            // Wait for completion
            executor.shutdown();
            boolean completed = executor.awaitTermination(5, TimeUnit.SECONDS);
            
            // Check for exceptions
            if (exception.get() != null) {
                fail("Exception during concurrent transfers: " + exception.get().getMessage());
            }
            
            // Should have completed (if not, likely a deadlock)
            if (!completed) {
                executor.shutdownNow();
                fail("Potential deadlock detected - transfers did not complete");
            }
            
            // Verify the final balances using DoubleEntryService
            BigDecimal finalDestBalance = doubleEntryService.calculateBalance(destinationAccount.getId());
            BigDecimal expectedBalance = transferAmount.multiply(new BigDecimal(numSenders));
            
            assertEquals(0, expectedBalance.compareTo(finalDestBalance),
                    "Destination account should have received all transfers");
            
            // Check each sender has the correct balance
            for (Account sender : senderAccounts) {
                BigDecimal senderFinalBalance = doubleEntryService.calculateBalance(sender.getId());
                assertEquals(0, new BigDecimal("90.00").compareTo(senderFinalBalance),
                        "Sender should have 90.00 remaining");
            }
        });
    }
    
    /**
     * Helper method to wait for a latch to count down
     */
    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while waiting", e);
        }
    }
} 