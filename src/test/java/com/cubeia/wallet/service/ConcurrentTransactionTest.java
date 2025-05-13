package com.cubeia.wallet.service;

import java.lang.reflect.Field;
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
    
    /**
     * Helper method to set account balance using reflection (for testing)
     */
    private void setAccountBalance(Account account, BigDecimal balance) {
        try {
            Field balanceField = Account.class.getDeclaredField("balance");
            balanceField.setAccessible(true);
            balanceField.set(account, balance);
            accountRepository.save(account);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set balance", e);
        }
    }
    
    /**
     * Test for concurrent transactions in opposite directions.
     * This test should detect potential deadlocks when transferring between
     * two accounts simultaneously in opposite directions.
     */
    @Test
    public void testConcurrentOppositeTransfers() {
        // Given - Create two accounts with initial balances
        Account account1 = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        Account account2 = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        
        setAccountBalance(account1, new BigDecimal("1000.00"));
        setAccountBalance(account2, new BigDecimal("1000.00"));
        
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
            
            // Verify the final balances
            Account finalAccount1 = accountRepository.findById(account1.getId()).get();
            Account finalAccount2 = accountRepository.findById(account2.getId()).get();
            
            // A1 started with 1000, sent 100, received 50 = 950
            // A2 started with 1000, sent 50, received 100 = 1050
            assertEquals(0, new BigDecimal("950.00").compareTo(finalAccount1.getBalance()),
                "Account 1 should have balance of 950.00");
            assertEquals(0, new BigDecimal("1050.00").compareTo(finalAccount2.getBalance()),
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
        Account destinationAccount = accountRepository.save(
            new Account(Currency.EUR, AccountType.MainAccount.getInstance())
        );
        
        // Create multiple sender accounts
        List<Account> senderAccounts = new ArrayList<>();
        for (int i = 0; i < numSenders; i++) {
            Account sender = accountRepository.save(
                new Account(Currency.EUR, AccountType.MainAccount.getInstance())
            );
            setAccountBalance(sender, new BigDecimal("100.00"));
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
            
            // Verify the final balances
            Account finalDestination = accountRepository.findById(destinationAccount.getId()).get();
            BigDecimal expectedBalance = transferAmount.multiply(new BigDecimal(numSenders));
            assertEquals(0, expectedBalance.compareTo(finalDestination.getBalance()),
                "Destination account should have balance of " + expectedBalance);
            
            // Check each sender has the correct remaining balance
            BigDecimal expectedSenderBalance = new BigDecimal("90.00");
            for (Account sender : senderAccounts) {
                Account finalSender = accountRepository.findById(sender.getId()).get();
                assertEquals(expectedSenderBalance.compareTo(finalSender.getBalance()), 0, 
                    "Account balance should be " + expectedSenderBalance);
            }
        });
    }
    
    /**
     * Helper method to await on a CountDownLatch and handle InterruptedException
     */
    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }
    }
} 