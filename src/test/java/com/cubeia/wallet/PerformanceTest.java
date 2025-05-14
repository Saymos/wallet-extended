package com.cubeia.wallet;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

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
 * Performance testing for the wallet application focusing on throughput and response times
 * under high concurrency. This test creates a larger number of concurrent transfers
 * to measure the scalability of the application.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class PerformanceTest {

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
    
    private static final int NUMBER_OF_CONCURRENT_THREADS = 25; // Reduced for stability
    private static final int TRANSFER_AMOUNT = 10;
    private static final int INITIAL_BALANCE = 10000; // Higher initial balance
    
    private List<Account> sourceAccounts;
    private List<Account> destinationAccounts;
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
     * Performance test for many-to-many transfers with high concurrency.
     * This test measures response times and throughput for concurrent transfers.
     */
    @Test
    void performanceTestManyToMany() throws InterruptedException {
        // Setup test data in this method to avoid transaction issues
        setupTestAccounts();
        
        // Capture account IDs
        List<UUID> sourceAccountIds = sourceAccounts.stream()
            .map(Account::getId)
            .toList();
        
        List<UUID> destinationAccountIds = destinationAccounts.stream()
            .map(Account::getId)
            .toList();
            
        // Create executor service for concurrent operations
        ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_CONCURRENT_THREADS);
        
        // Use countdown latch to make threads start at the same time
        CountDownLatch startLatch = new CountDownLatch(1);
        
        // Use countdown latch to wait for all threads to finish
        CountDownLatch endLatch = new CountDownLatch(NUMBER_OF_CONCURRENT_THREADS);
        
        // Track metrics
        AtomicInteger successfulTransfers = new AtomicInteger(0);
        AtomicInteger failedTransfers = new AtomicInteger(0);
        List<Long> responseTimes = new ArrayList<>();
        Object responseTimesLock = new Object();
        
        // Submit concurrent transfer tasks
        for (int i = 0; i < NUMBER_OF_CONCURRENT_THREADS; i++) {
            final int index = i;
            final UUID sourceAccountId = sourceAccountIds.get(index);
            // Use a different destination account for each source to create a mesh pattern
            final UUID destinationAccountId = destinationAccountIds.get((index + 1) % NUMBER_OF_CONCURRENT_THREADS);
            
            executorService.submit(() -> {
                try {
                    // Wait for the signal to start
                    startLatch.await();
                    
                    // Generate a unique reference ID for this transfer
                    String referenceId = "PERF-" + sourceAccountId + "-" + destinationAccountId + "-" + UUID.randomUUID();
                    
                    // Measure response time
                    Instant start = Instant.now();
                    
                    // Execute the transfer
                    transactionService.transfer(
                            sourceAccountId,
                            destinationAccountId,
                            new BigDecimal(TRANSFER_AMOUNT),
                            referenceId
                    );
                    
                    // Calculate response time in milliseconds
                    long responseTime = Duration.between(start, Instant.now()).toMillis();
                    synchronized (responseTimesLock) {
                        responseTimes.add(responseTime);
                    }
                    
                    successfulTransfers.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Transfer failed: " + e.getMessage());
                    failedTransfers.incrementAndGet();
                } finally {
                    // Signal that this thread has completed
                    endLatch.countDown();
                }
            });
        }
        
        // Capture start time for throughput calculation
        Instant testStart = Instant.now();
        
        // Start all transfers simultaneously
        startLatch.countDown();
        
        // Wait for all transfers to complete (with a timeout)
        boolean allTransfersCompleted = endLatch.await(60, TimeUnit.SECONDS);
        
        // Capture end time for throughput calculation
        Instant testEnd = Instant.now();
        long testDurationMs = Duration.between(testStart, testEnd).toMillis();
        
        // Shutdown executor
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        
        // Calculate performance metrics
        int totalTransfers = successfulTransfers.get();
        double throughputTps = (totalTransfers * 1000.0) / testDurationMs; // Transfers per second
        
        // Calculate response time statistics
        long totalResponseTime = 0;
        long minResponseTime = responseTimes.isEmpty() ? 0 : Long.MAX_VALUE;
        long maxResponseTime = 0;
        
        for (long time : responseTimes) {
            totalResponseTime += time;
            if (time < minResponseTime) minResponseTime = time;
            if (time > maxResponseTime) maxResponseTime = time;
        }
        
        double avgResponseTime = responseTimes.isEmpty() ? 0 : totalResponseTime / (double) responseTimes.size();
        
        // Sort response times for percentile calculations
        responseTimes.sort(Long::compare);
        long p50ResponseTime = responseTimes.isEmpty() ? 0 : responseTimes.get(responseTimes.size() / 2);
        long p95ResponseTime = responseTimes.isEmpty() ? 0 : responseTimes.get((int)(responseTimes.size() * 0.95));
        long p99ResponseTime = responseTimes.isEmpty() ? 0 : responseTimes.get((int)(responseTimes.size() * 0.99));
        
        // Log performance results
        System.out.println("\n==== PERFORMANCE TEST RESULTS ====");
        System.out.println("Concurrent Threads: " + NUMBER_OF_CONCURRENT_THREADS);
        System.out.println("Successful Transfers: " + successfulTransfers.get());
        System.out.println("Failed Transfers: " + failedTransfers.get());
        System.out.println("Test Duration: " + testDurationMs + " ms");
        System.out.println("Throughput: " + String.format("%.2f", throughputTps) + " transactions per second");
        System.out.println("Average Response Time: " + String.format("%.2f", avgResponseTime) + " ms");
        System.out.println("Min Response Time: " + minResponseTime + " ms");
        System.out.println("Max Response Time: " + maxResponseTime + " ms");
        System.out.println("50th Percentile (Median) Response Time: " + p50ResponseTime + " ms");
        System.out.println("95th Percentile Response Time: " + p95ResponseTime + " ms");
        System.out.println("99th Percentile Response Time: " + p99ResponseTime + " ms");
        System.out.println("====================================\n");
        
        // Assertions
        assertTrue(allTransfersCompleted, "Not all transfers completed within the timeout period");
        assertTrue(failedTransfers.get() < NUMBER_OF_CONCURRENT_THREADS / 4, 
                "Too many transfers failed during the performance test");
        
        // Verify final balances if there were successful transfers
        if (successfulTransfers.get() > 0) {
            // Refresh all accounts
            List<Account> refreshedSourceAccounts = new ArrayList<>();
            for (UUID id : sourceAccountIds) {
                accountRepository.findById(id).ifPresent(refreshedSourceAccounts::add);
            }
            
            List<Account> refreshedDestAccounts = new ArrayList<>();
            for (UUID id : destinationAccountIds) {
                accountRepository.findById(id).ifPresent(refreshedDestAccounts::add);
            }
            
            // Calculate total system balance
            BigDecimal totalBalance = BigDecimal.ZERO;
            
            for (Account account : refreshedSourceAccounts) {
                totalBalance = totalBalance.add(account.getBalance());
            }
            
            for (Account account : refreshedDestAccounts) {
                totalBalance = totalBalance.add(account.getBalance());
            }
            
            // Expected total: INITIAL_BALANCE * NUMBER_OF_CONCURRENT_THREADS (since destination accounts start with 0)
            BigDecimal expectedTotalBalance = new BigDecimal(INITIAL_BALANCE * NUMBER_OF_CONCURRENT_THREADS);
            
            // Verify total balance with tolerance for successful transfers
            BigDecimal tolerance = new BigDecimal(TRANSFER_AMOUNT * (NUMBER_OF_CONCURRENT_THREADS - successfulTransfers.get()));
            BigDecimal difference = expectedTotalBalance.subtract(totalBalance).abs();
            assertTrue(difference.compareTo(tolerance) <= 0,
                    "Total system balance difference exceeds tolerance. " +
                    "Expected: " + expectedTotalBalance + ", Actual: " + totalBalance);
        }
        
        // Print summary of results
        System.out.println("Performance test completed with throughput of " + 
                String.format("%.2f", throughputTps) + " TPS");
    }
    
    /**
     * Performance test for database contention with multiple transfers involving the same account.
     * This test measures the impact of locking on performance.
     */
    @Test
    void performanceTestHighContention() throws InterruptedException {
        // Setup test data in this method to avoid transaction issues
        setupTestAccounts();
        
        // Create a single destination account that all sources will transfer to
        Account sharedDestination = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        sharedDestination = accountRepository.save(sharedDestination);
        final UUID sharedDestinationId = sharedDestination.getId();
        
        // Capture source account IDs
        List<UUID> sourceAccountIds = sourceAccounts.stream()
            .map(Account::getId)
            .toList();
            
        // Create executor service for concurrent operations
        ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_CONCURRENT_THREADS);
        
        // Use countdown latch to make threads start at the same time
        CountDownLatch startLatch = new CountDownLatch(1);
        
        // Use countdown latch to wait for all threads to finish
        CountDownLatch endLatch = new CountDownLatch(NUMBER_OF_CONCURRENT_THREADS);
        
        // Track metrics
        AtomicInteger successfulTransfers = new AtomicInteger(0);
        AtomicInteger failedTransfers = new AtomicInteger(0);
        List<Long> responseTimes = new ArrayList<>();
        Object responseTimesLock = new Object();
        
        // Submit concurrent transfer tasks - all to the same destination to create contention
        for (int i = 0; i < NUMBER_OF_CONCURRENT_THREADS; i++) {
            final UUID sourceAccountId = sourceAccountIds.get(i);
            
            executorService.submit(() -> {
                try {
                    // Wait for the signal to start
                    startLatch.await();
                    
                    // Generate a unique reference ID for this transfer
                    String referenceId = "CONTENTION-" + sourceAccountId + "-" + UUID.randomUUID();
                    
                    // Measure response time
                    Instant start = Instant.now();
                    
                    // Execute the transfer to the shared destination (high contention)
                    transactionService.transfer(
                            sourceAccountId,
                            sharedDestinationId,
                            new BigDecimal(TRANSFER_AMOUNT),
                            referenceId
                    );
                    
                    // Calculate response time in milliseconds
                    long responseTime = Duration.between(start, Instant.now()).toMillis();
                    synchronized (responseTimesLock) {
                        responseTimes.add(responseTime);
                    }
                    
                    successfulTransfers.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Transfer failed: " + e.getMessage());
                    failedTransfers.incrementAndGet();
                } finally {
                    // Signal that this thread has completed
                    endLatch.countDown();
                }
            });
        }
        
        // Capture start time for throughput calculation
        Instant testStart = Instant.now();
        
        // Start all transfers simultaneously
        startLatch.countDown();
        
        // Wait for all transfers to complete (with a timeout)
        boolean allTransfersCompleted = endLatch.await(60, TimeUnit.SECONDS);
        
        // Capture end time for throughput calculation
        Instant testEnd = Instant.now();
        long testDurationMs = Duration.between(testStart, testEnd).toMillis();
        
        // Shutdown executor
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        
        // Calculate performance metrics
        int totalTransfers = successfulTransfers.get();
        double throughputTps = (totalTransfers * 1000.0) / testDurationMs; // Transfers per second
        
        // Calculate response time statistics
        long totalResponseTime = 0;
        long minResponseTime = responseTimes.isEmpty() ? 0 : Long.MAX_VALUE;
        long maxResponseTime = 0;
        
        for (long time : responseTimes) {
            totalResponseTime += time;
            if (time < minResponseTime) minResponseTime = time;
            if (time > maxResponseTime) maxResponseTime = time;
        }
        
        double avgResponseTime = responseTimes.isEmpty() ? 0 : totalResponseTime / (double) responseTimes.size();
        
        // Sort response times for percentile calculations
        responseTimes.sort(Long::compare);
        long p50ResponseTime = responseTimes.isEmpty() ? 0 : responseTimes.get(responseTimes.size() / 2);
        long p95ResponseTime = responseTimes.isEmpty() ? 0 : responseTimes.get((int)(responseTimes.size() * 0.95));
        long p99ResponseTime = responseTimes.isEmpty() ? 0 : responseTimes.get((int)(responseTimes.size() * 0.99));
        
        // Log performance results
        System.out.println("\n==== HIGH CONTENTION PERFORMANCE TEST RESULTS ====");
        System.out.println("Concurrent Threads: " + NUMBER_OF_CONCURRENT_THREADS);
        System.out.println("Successful Transfers: " + successfulTransfers.get());
        System.out.println("Failed Transfers: " + failedTransfers.get());
        System.out.println("Test Duration: " + testDurationMs + " ms");
        System.out.println("Throughput: " + String.format("%.2f", throughputTps) + " transactions per second");
        System.out.println("Average Response Time: " + String.format("%.2f", avgResponseTime) + " ms");
        System.out.println("Min Response Time: " + minResponseTime + " ms");
        System.out.println("Max Response Time: " + maxResponseTime + " ms");
        System.out.println("50th Percentile (Median) Response Time: " + p50ResponseTime + " ms");
        System.out.println("95th Percentile Response Time: " + p95ResponseTime + " ms");
        System.out.println("99th Percentile Response Time: " + p99ResponseTime + " ms");
        System.out.println("====================================\n");
        
        // Assertions
        assertTrue(allTransfersCompleted, "Not all transfers completed within the timeout period");
        assertTrue(failedTransfers.get() < NUMBER_OF_CONCURRENT_THREADS / 4, 
                "Too many transfers failed during the high contention test");
        
        // Verify final balance of the shared destination if transfers succeeded
        if (successfulTransfers.get() > 0) {
            Account refreshedDestination = accountRepository.findById(sharedDestinationId).orElseThrow();
            BigDecimal expectedDestinationBalance = new BigDecimal(TRANSFER_AMOUNT * successfulTransfers.get());
            BigDecimal difference = refreshedDestination.getBalance().subtract(expectedDestinationBalance).abs();
            assertTrue(difference.compareTo(new BigDecimal("0.001")) <= 0,
                    "Destination account balance doesn't match expected value");
        }
        
        // Print summary
        System.out.println("High contention performance test completed with throughput of " + 
                String.format("%.2f", throughputTps) + " TPS");
    }
    
    /**
     * Helper method to set up test accounts outside of @BeforeEach to avoid transaction issues
     */
    private void setupTestAccounts() {
        // Clear any existing data to avoid conflicts
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        
        // Create system account with a large balance for testing
        systemAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        systemAccount = accountRepository.save(systemAccount);
        
        // Set the system account balance directly to avoid insufficient funds issues
        systemAccount = setAccountBalance(systemAccount, new BigDecimal("10000000.00"));
        
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
            
            fundingTransaction.execute(fundingTransaction, systemAccount, account);
            accountRepository.save(systemAccount);
            accountRepository.save(account);
            transactionRepository.save(fundingTransaction);
            
            sourceAccounts.add(account);
        }
        
        // Create destination accounts with zero balance
        destinationAccounts = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_CONCURRENT_THREADS; i++) {
            Account account = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
            account = accountRepository.save(account);
            destinationAccounts.add(account);
        }
    }
} 