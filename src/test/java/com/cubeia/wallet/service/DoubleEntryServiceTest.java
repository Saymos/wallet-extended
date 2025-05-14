package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyList;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.exception.BalanceVerificationException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.EntryType;
import com.cubeia.wallet.model.LedgerEntry;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.LedgerEntryRepository;

/**
 * Unit tests for the DoubleEntryService.
 */
@ExtendWith(MockitoExtension.class)
public class DoubleEntryServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    
    @Mock
    private AccountRepository accountRepository;
    
    @InjectMocks
    private DoubleEntryService doubleEntryService;
    
    private UUID fromAccountId;
    private UUID toAccountId;
    private UUID transactionId;
    private Transaction transaction;
    private Account fromAccount;
    private Account toAccount;
    
    @BeforeEach
    public void setUp() {
        // Setup test data
        fromAccountId = UUID.randomUUID();
        toAccountId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
        
        // Create test accounts
        fromAccount = new Account(Currency.USD, AccountType.MainAccount.getInstance());
        toAccount = new Account(Currency.USD, AccountType.MainAccount.getInstance());
        
        // Set account IDs and balances using reflection
        try {
            var idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(fromAccount, fromAccountId);
            idField.set(toAccount, toAccountId);
            
            var balanceField = Account.class.getDeclaredField("balance");
            balanceField.setAccessible(true);
            balanceField.set(fromAccount, BigDecimal.valueOf(1000));
            balanceField.set(toAccount, BigDecimal.valueOf(500));
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up test accounts", e);
        }
        
        // Create test transaction
        transaction = new Transaction(
            fromAccountId,
            toAccountId,
            BigDecimal.valueOf(100),
            TransactionType.TRANSFER,
            Currency.USD
        );
        
        // Set the transaction ID using reflection
        try {
            var field = Transaction.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(transaction, transactionId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set transaction ID", e);
        }
        
        // Setup mock behavior
        lenient().when(accountRepository.existsById(fromAccountId)).thenReturn(true);
        lenient().when(accountRepository.existsById(toAccountId)).thenReturn(true);
        
        lenient().when(ledgerEntryRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<LedgerEntry> entries = invocation.getArgument(0);
            // Set IDs for the entries
            entries.forEach(entry -> {
                try {
                    // Set a UUID for testing
                    var field = LedgerEntry.class.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(entry, UUID.randomUUID());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            return entries;
        });
    }
    
    @Test
    public void testCreateTransferEntries() {
        // Arrange is done in setUp()
        
        // Act
        List<LedgerEntry> entries = doubleEntryService.createTransferEntries(transaction);
        
        // Assert
        assertNotNull(entries);
        assertEquals(2, entries.size(), "Should create two ledger entries");
        
        // Find debit and credit entries
        LedgerEntry debitEntry = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .findFirst()
                .orElseThrow();
        
        LedgerEntry creditEntry = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .findFirst()
                .orElseThrow();
        
        // Verify debit entry
        assertEquals(fromAccountId, debitEntry.getAccountId(), "Debit should be for fromAccount");
        assertEquals(transactionId, debitEntry.getTransactionId(), "Transaction ID should match");
        assertEquals(BigDecimal.valueOf(100), debitEntry.getAmount(), "Amount should match");
        
        // Verify credit entry
        assertEquals(toAccountId, creditEntry.getAccountId(), "Credit should be for toAccount");
        assertEquals(transactionId, creditEntry.getTransactionId(), "Transaction ID should match");
        assertEquals(BigDecimal.valueOf(100), creditEntry.getAmount(), "Amount should match");
        
        // Verify repository was called
        verify(ledgerEntryRepository).saveAll(anyList());
    }
    
    @Test
    public void testCreateTransferEntries_AccountNotFound() {
        // Arrange
        when(accountRepository.existsById(fromAccountId)).thenReturn(false);
        
        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            doubleEntryService.createTransferEntries(transaction);
        }, "Should throw AccountNotFoundException when fromAccount doesn't exist");
        
        // Arrange for toAccount not existing
        when(accountRepository.existsById(fromAccountId)).thenReturn(true);
        when(accountRepository.existsById(toAccountId)).thenReturn(false);
        
        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            doubleEntryService.createTransferEntries(transaction);
        }, "Should throw AccountNotFoundException when toAccount doesn't exist");
    }
    
    @Test
    public void testCalculateBalance() {
        // Arrange
        BigDecimal expectedBalance = new BigDecimal("750.00");
        when(ledgerEntryRepository.calculateBalance(fromAccountId)).thenReturn(expectedBalance);
        
        // Act
        BigDecimal actualBalance = doubleEntryService.calculateBalance(fromAccountId);
        
        // Assert
        assertEquals(expectedBalance, actualBalance, "Should return the balance from repository");
        verify(ledgerEntryRepository).calculateBalance(fromAccountId);
    }
    
    @Test
    public void testCalculateBalance_AccountNotFound() {
        // Arrange
        when(accountRepository.existsById(fromAccountId)).thenReturn(false);
        
        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            doubleEntryService.calculateBalance(fromAccountId);
        }, "Should throw AccountNotFoundException when account doesn't exist");
    }
    
    @Test
    public void testVerifyBalance_Success() {
        // Arrange
        BigDecimal expectedBalance = new BigDecimal("1000.00");
        when(ledgerEntryRepository.calculateBalance(fromAccountId)).thenReturn(expectedBalance);
        
        // Act - should not throw
        doubleEntryService.verifyBalance(fromAccountId, expectedBalance);
        
        // Assert is implicit in no exception being thrown
        verify(ledgerEntryRepository).calculateBalance(fromAccountId);
    }
    
    @Test
    public void testVerifyBalance_Failure() {
        // Arrange
        BigDecimal expectedBalance = new BigDecimal("1000.00");
        BigDecimal actualBalance = new BigDecimal("999.99");
        when(ledgerEntryRepository.calculateBalance(fromAccountId)).thenReturn(actualBalance);
        
        // Act & Assert
        BalanceVerificationException exception = assertThrows(BalanceVerificationException.class, () -> {
            doubleEntryService.verifyBalance(fromAccountId, expectedBalance);
        }, "Should throw BalanceVerificationException when balances don't match");
        
        assertEquals(fromAccountId, exception.getAccountId(), "Exception should contain account ID");
        assertEquals(expectedBalance, exception.getExpectedBalance(), "Exception should contain expected balance");
        assertEquals(actualBalance, exception.getActualBalance(), "Exception should contain actual balance");
    }
    
    @Test
    public void testVerifyAccountBalance_Success() {
        // Arrange
        when(ledgerEntryRepository.calculateBalance(fromAccountId)).thenReturn(fromAccount.getBalance());
        
        // Act - should not throw
        doubleEntryService.verifyAccountBalance(fromAccount);
        
        // Assert is implicit in no exception being thrown
        verify(ledgerEntryRepository).calculateBalance(fromAccountId);
    }
    
    @Test
    public void testVerifyAccountBalance_Failure() {
        // Arrange
        BigDecimal actualBalance = new BigDecimal("990.00");
        when(ledgerEntryRepository.calculateBalance(fromAccountId)).thenReturn(actualBalance);
        
        // Act & Assert
        BalanceVerificationException exception = assertThrows(BalanceVerificationException.class, () -> {
            doubleEntryService.verifyAccountBalance(fromAccount);
        }, "Should throw BalanceVerificationException when balances don't match");
        
        assertEquals(fromAccountId, exception.getAccountId(), "Exception should contain account ID");
        assertEquals(fromAccount.getBalance(), exception.getExpectedBalance(), "Exception should contain expected balance");
        assertEquals(actualBalance, exception.getActualBalance(), "Exception should contain actual balance");
    }
    
    @Test
    public void testGetAccountEntries() {
        // Arrange
        LedgerEntry entry1 = createTestLedgerEntry(fromAccountId, transactionId, EntryType.DEBIT, BigDecimal.valueOf(100));
        LedgerEntry entry2 = createTestLedgerEntry(fromAccountId, transactionId, EntryType.CREDIT, BigDecimal.valueOf(50));
        List<LedgerEntry> expectedEntries = List.of(entry1, entry2);
        
        when(ledgerEntryRepository.findByAccountIdOrderByTimestampDesc(fromAccountId))
            .thenReturn(expectedEntries);
        
        // Act
        List<LedgerEntry> entries = doubleEntryService.getAccountEntries(fromAccountId);
        
        // Assert
        assertEquals(expectedEntries, entries, "Should return entries from repository");
        verify(ledgerEntryRepository).findByAccountIdOrderByTimestampDesc(fromAccountId);
    }
    
    @Test
    public void testGetTransactionEntries() {
        // Arrange
        LedgerEntry entry1 = createTestLedgerEntry(fromAccountId, transactionId, EntryType.DEBIT, BigDecimal.valueOf(100));
        LedgerEntry entry2 = createTestLedgerEntry(toAccountId, transactionId, EntryType.CREDIT, BigDecimal.valueOf(100));
        List<LedgerEntry> expectedEntries = List.of(entry1, entry2);
        
        when(ledgerEntryRepository.findByTransactionId(transactionId))
            .thenReturn(expectedEntries);
        
        // Act
        List<LedgerEntry> entries = doubleEntryService.getTransactionEntries(transactionId);
        
        // Assert
        assertEquals(expectedEntries, entries, "Should return entries from repository");
        verify(ledgerEntryRepository).findByTransactionId(transactionId);
    }
    
    /**
     * Test for thread safety with concurrent operations
     */
    @Test
    public void testConcurrentOperations() throws InterruptedException {
        // Arrange
        final int numThreads = 10;
        final CountDownLatch latch = new CountDownLatch(numThreads);
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        // Set up repository mock for concurrent operations
        when(ledgerEntryRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<LedgerEntry> entries = invocation.getArgument(0);
            // Simulate some processing time to increase chance of concurrent issues
            Thread.sleep(10);
            // Set IDs for the entries
            entries.forEach(entry -> {
                try {
                    var field = LedgerEntry.class.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(entry, UUID.randomUUID());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            return entries;
        });
        
        // Act - submit concurrent tasks
        for (int i = 0; i < numThreads; i++) {
            final UUID transId = UUID.randomUUID();
            final Transaction tx = new Transaction(
                fromAccountId,
                toAccountId,
                BigDecimal.valueOf(100),
                TransactionType.TRANSFER,
                Currency.USD
            );
            
            // Set the transaction ID using reflection
            try {
                var field = Transaction.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(tx, transId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set transaction ID", e);
            }
            
            executor.submit(() -> {
                try {
                    doubleEntryService.createTransferEntries(tx);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all threads to complete
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Assert
        assertTrue(completed, "All concurrent operations should complete");
        verify(ledgerEntryRepository, times(numThreads)).saveAll(anyList());
    }
    
    private LedgerEntry createTestLedgerEntry(UUID accountId, UUID transactionId, EntryType entryType, BigDecimal amount) {
        LedgerEntry entry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .entryType(entryType)
                .amount(amount)
                .description("Test entry")
                .build();
        
        try {
            // Set a UUID for testing
            var field = LedgerEntry.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entry, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        return entry;
    }
} 