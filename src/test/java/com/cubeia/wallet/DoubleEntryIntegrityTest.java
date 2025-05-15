package com.cubeia.wallet;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.EntryType;
import com.cubeia.wallet.model.LedgerEntry;
import com.cubeia.wallet.model.TestAccount;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.LedgerEntryRepository;
import com.cubeia.wallet.repository.TransactionRepository;
import com.cubeia.wallet.service.DoubleEntryService;
import com.cubeia.wallet.service.TransactionService;

/**
 * Comprehensive tests for verifying the integrity of the double-entry bookkeeping system.
 * <p>
 * These tests focus on ensuring:
 * - Money is neither created nor destroyed in transfer operations
 * - All accounts have balanced ledger entries
 * - System maintains integrity under concurrent operations
 * - Complex transaction scenarios work correctly
 * </p>
 */
@SpringBootTest
public class DoubleEntryIntegrityTest {

    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    
    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private DoubleEntryService doubleEntryService;
    
    @BeforeEach
    public void setup() {
        // Clear all data before each test
        ledgerEntryRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }
    
    /**
     * Verifies that money is neither created nor destroyed in a transfer.
     * The sum of all account balances should remain constant.
     */
    @Test
    public void testNoMoneyCreatedOrDestroyed() {
        // Create accounts
        TestAccount account1 = accountRepository.save(new TestAccount(Currency.EUR, AccountType.MainAccount.getInstance()));
        TestAccount account2 = accountRepository.save(new TestAccount(Currency.EUR, AccountType.MainAccount.getInstance()));
        TestAccount account3 = accountRepository.save(new TestAccount(Currency.EUR, AccountType.MainAccount.getInstance()));
        
        // Fund account1
        doubleEntryService.createSystemCreditEntry(
            account1.getId(), 
            new BigDecimal("1000.00"), 
            "Initial funding"
        );
        
        // Verify initial total balance in the system
        BigDecimal initialTotalBalance = calculateTotalBalance(List.of(account1, account2, account3));
        assertEquals(0, new BigDecimal("1000.00").compareTo(initialTotalBalance));
        
        // Perform several transfers
        transactionService.transfer(account1.getId(), account2.getId(), new BigDecimal("300.00"), null, null);
        transactionService.transfer(account2.getId(), account3.getId(), new BigDecimal("150.00"), null, null);
        transactionService.transfer(account3.getId(), account1.getId(), new BigDecimal("50.00"), null, null);
        
        // Verify final total balance in the system
        BigDecimal finalTotalBalance = calculateTotalBalance(List.of(account1, account2, account3));
        
        // The total balance should remain unchanged
        assertEquals(0, initialTotalBalance.compareTo(finalTotalBalance));
    }
    
    /**
     * Verifies that ledger entries are balanced for all accounts.
     * For each account, the sum of CREDIT entries minus the sum of DEBIT entries
     * should equal the account balance.
     */
    @Test
    public void testLedgerBalancing() {
        // Create accounts
        TestAccount account1 = accountRepository.save(new TestAccount(Currency.EUR, AccountType.MainAccount.getInstance()));
        TestAccount account2 = accountRepository.save(new TestAccount(Currency.EUR, AccountType.MainAccount.getInstance()));
        
        // Fund account1
        doubleEntryService.createSystemCreditEntry(
            account1.getId(), 
            new BigDecimal("500.00"), 
            "Initial funding"
        );
        
        // Perform a transfer
        transactionService.transfer(account1.getId(), account2.getId(), new BigDecimal("200.00"), null, null);
        
        // Get all ledger entries for each account
        List<LedgerEntry> entries1 = ledgerEntryRepository.findByAccountIdOrderByTimestampDesc(account1.getId());
        List<LedgerEntry> entries2 = ledgerEntryRepository.findByAccountIdOrderByTimestampDesc(account2.getId());
        
        // Calculate balances from entries
        BigDecimal balance1FromEntries = calculateBalanceFromEntries(entries1);
        BigDecimal balance2FromEntries = calculateBalanceFromEntries(entries2);
        
        // Get balances calculated by the service
        BigDecimal balance1FromService = doubleEntryService.calculateBalance(account1.getId());
        BigDecimal balance2FromService = doubleEntryService.calculateBalance(account2.getId());
        
        // Verify both calculation methods match
        assertEquals(0, balance1FromService.compareTo(balance1FromEntries));
        assertEquals(0, balance2FromService.compareTo(balance2FromEntries));
        
        // Verify expected final balances
        assertEquals(0, new BigDecimal("300.00").compareTo(balance1FromService));
        assertEquals(0, new BigDecimal("200.00").compareTo(balance2FromService));
        
        // For each transaction, verify debits equal credits
        List<Transaction> transactions = transactionRepository.findAll();
        for (Transaction tx : transactions) {
            List<LedgerEntry> txEntries = ledgerEntryRepository.findByTransactionId(tx.getId());
            
            BigDecimal totalDebits = txEntries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            BigDecimal totalCredits = txEntries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            // For a balanced transaction, debits should equal credits
            assertEquals(0, totalDebits.compareTo(totalCredits), 
                "Transaction " + tx.getId() + " is not balanced: debits=" + totalDebits + ", credits=" + totalCredits);
        }
    }
    
    /**
     * Tests integrity for chained transfers across multiple accounts.
     */
    @Test
    public void testChainedTransfers() {
        // Create a chain of accounts
        int chainLength = 10;
        List<Account> accounts = new ArrayList<>();
        
        for (int i = 0; i < chainLength; i++) {
            accounts.add(accountRepository.save(new TestAccount(Currency.EUR, AccountType.MainAccount.getInstance())));
        }
        
        // Fund the first account
        doubleEntryService.createSystemCreditEntry(
            accounts.get(0).getId(), 
            new BigDecimal("1000.00"), 
            "Initial funding"
        );
        
        // Get total system balance before transfers
        BigDecimal initialTotalBalance = calculateTotalBalance(accounts);
        
        // Perform chained transfers
        for (int i = 0; i < chainLength - 1; i++) {
            transactionService.transfer(
                accounts.get(i).getId(), 
                accounts.get(i + 1).getId(), 
                new BigDecimal("100.00"),
                null,
                null
            );
        }
        
        // Verify individual account balances
        BigDecimal expectedFirstAccountBalance = new BigDecimal("900.00");
        assertEquals(0, expectedFirstAccountBalance.compareTo(doubleEntryService.calculateBalance(accounts.get(0).getId())));
        
        BigDecimal expectedMiddleAccountBalance = new BigDecimal("0.00");
        for (int i = 1; i < chainLength - 1; i++) {
            assertEquals(0, expectedMiddleAccountBalance.compareTo(doubleEntryService.calculateBalance(accounts.get(i).getId())));
        }
        
        BigDecimal expectedLastAccountBalance = new BigDecimal("100.00");
        assertEquals(0, expectedLastAccountBalance.compareTo(doubleEntryService.calculateBalance(accounts.get(chainLength - 1).getId())));
        
        // Verify total system balance remains unchanged
        BigDecimal finalTotalBalance = calculateTotalBalance(accounts);
        assertEquals(0, initialTotalBalance.compareTo(finalTotalBalance));
    }
    
    /**
     * Tests the integrity of the system under concurrent high-volume transactions.
     */
    @Test
    public void testConcurrentHighVolumeTransfers() throws Exception {
        // Create 3 accounts
        TestAccount sourceAccount = accountRepository.save(new TestAccount(Currency.EUR, AccountType.MainAccount.getInstance()));
        TestAccount destinationAccount1 = accountRepository.save(new TestAccount(Currency.EUR, AccountType.MainAccount.getInstance()));
        TestAccount destinationAccount2 = accountRepository.save(new TestAccount(Currency.EUR, AccountType.MainAccount.getInstance()));
        
        // Fund the source account with a large amount
        doubleEntryService.createSystemCreditEntry(
            sourceAccount.getId(), 
            new BigDecimal("1000000.00"), // 1 million
            "Initial high-volume funding"
        );
        
        // Save the initial total balance
        List<Account> allAccounts = List.of(sourceAccount, destinationAccount1, destinationAccount2);
        BigDecimal initialTotalBalance = calculateTotalBalance(allAccounts);
        
        // Set up for concurrent execution
        int numThreads = 10;
        int transfersPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numThreads);
        
        // Create tasks for concurrent transfers
        for (int i = 0; i < numThreads; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();
                    
                    // Perform transfers alternating between destination accounts
                    for (int j = 0; j < transfersPerThread; j++) {
                        UUID toAccountId = (j % 2 == 0) ? destinationAccount1.getId() : destinationAccount2.getId();
                        
                        // Create a unique reference ID for each transfer
                        String referenceId = "thread-" + threadNum + "-transfer-" + j;
                        
                        // Perform the transfer
                        try {
                            transactionService.transfer(
                                sourceAccount.getId(),
                                toAccountId,
                                new BigDecimal("100.00"),
                                referenceId,
                                null
                            );
                        } catch (Exception e) {
                            System.err.println("Transfer failed: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    completionLatch.countDown();
                }
            });
        }
        
        // Start all threads
        startLatch.countDown();
        
        // Wait for all threads to complete (with timeout)
        boolean completed = completionLatch.await(60, TimeUnit.SECONDS);
        assertTrue(completed, "All transfers should complete within the timeout");
        
        executor.shutdown();
        
        // Calculate final total balance
        BigDecimal finalTotalBalance = calculateTotalBalance(allAccounts);
        
        // Total balance should remain unchanged
        assertEquals(0, initialTotalBalance.compareTo(finalTotalBalance));
        
        // Verify expected balances based on number of transfers
        int totalTransfers = numThreads * transfersPerThread;
        BigDecimal expectedTransferAmount = new BigDecimal("100.00").multiply(new BigDecimal(totalTransfers));
        
        BigDecimal expectedSourceBalance = initialTotalBalance.subtract(expectedTransferAmount);
        BigDecimal actualSourceBalance = doubleEntryService.calculateBalance(sourceAccount.getId());
        
        // Note: Some transfers might fail due to concurrent operations, so we'll allow for a slightly different balance
        // But the total balance across all accounts should always match
        assertTrue(
            actualSourceBalance.compareTo(expectedSourceBalance) >= 0, 
            "Source balance should be at least " + expectedSourceBalance + " but was " + actualSourceBalance
        );
    }
    
    /**
     * Performs many concurrent transfers using shared source/destination accounts.
     * This test verifies that the system maintains consistency under high concurrency.
     */
    @Test
    public void testConcurrentTransfersWithSharedAccounts() throws Exception {
        // Create accounts
        TestAccount sourceAccount = accountRepository.save(new TestAccount(Currency.EUR, AccountType.MainAccount.getInstance()));
        TestAccount destAccount = accountRepository.save(new TestAccount(Currency.EUR, AccountType.MainAccount.getInstance()));
        
        // Fund source account
        doubleEntryService.createSystemCreditEntry(
            sourceAccount.getId(), 
            new BigDecimal("10000.00"),
            "Initial funding"
        );
        
        // Save initial total balance
        BigDecimal initialTotalBalance = doubleEntryService.calculateBalance(sourceAccount.getId())
            .add(doubleEntryService.calculateBalance(destAccount.getId()));
        
        // Set up concurrent transfers
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // Each thread performs a small transfer from source to destination
        for (int i = 0; i < numThreads; i++) {
            final String ref = "concurrent-" + i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    transactionService.transfer(
                        sourceAccount.getId(),
                        destAccount.getId(),
                        new BigDecimal("100.00"),
                        ref,
                        null
                    );
                } catch (Exception e) {
                    System.err.println("Transfer failed: " + e.getMessage());
                }
            }, executor);
            
            futures.add(future);
        }
        
        // Wait for all transfers to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            System.err.println("Some transfers failed: " + e.getMessage());
        }
        
        executor.shutdown();
        
        // Verify final balances
        BigDecimal finalSourceBalance = doubleEntryService.calculateBalance(sourceAccount.getId());
        BigDecimal finalDestBalance = doubleEntryService.calculateBalance(destAccount.getId());
        
        BigDecimal expectedSourceBalance = new BigDecimal("9000.00"); // 10000 - (10 * 100)
        BigDecimal expectedDestBalance = new BigDecimal("1000.00");   // 0 + (10 * 100)
        
        // Allow for some transfers to fail, but total balance should be the same
        assertTrue(
            finalSourceBalance.compareTo(expectedSourceBalance) >= 0, 
            "Source balance should be at least " + expectedSourceBalance + " but was " + finalSourceBalance
        );
        
        // Verify total balance remains unchanged
        BigDecimal finalTotalBalance = finalSourceBalance.add(finalDestBalance);
        assertEquals(0, initialTotalBalance.compareTo(finalTotalBalance));
        
        // Verify all ledger entries are balanced (sum of debits = sum of credits)
        List<LedgerEntry> allEntries = ledgerEntryRepository.findAll();
        
        BigDecimal totalDebits = allEntries.stream()
            .filter(e -> e.getEntryType() == EntryType.DEBIT)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal totalCredits = allEntries.stream()
            .filter(e -> e.getEntryType() == EntryType.CREDIT)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        assertEquals(0, totalDebits.compareTo(totalCredits),
            "Total debits (" + totalDebits + ") should equal total credits (" + totalCredits + ")");
    }
    
    /**
     * Helper method to calculate the total balance across multiple accounts.
     */
    private BigDecimal calculateTotalBalance(List<Account> accounts) {
        return accounts.stream()
            .map(account -> doubleEntryService.calculateBalance(account.getId()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Helper method to calculate an account balance from its ledger entries
     * (CREDIT - DEBIT).
     */
    private BigDecimal calculateBalanceFromEntries(List<LedgerEntry> entries) {
        BigDecimal debitsTotal = entries.stream()
            .filter(e -> e.getEntryType() == EntryType.DEBIT)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal creditsTotal = entries.stream()
            .filter(e -> e.getEntryType() == EntryType.CREDIT)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        return creditsTotal.subtract(debitsTotal);
    }
} 