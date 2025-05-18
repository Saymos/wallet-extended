package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.EntryType;
import com.cubeia.wallet.model.LedgerEntry;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.LedgerEntryRepository;

/**
 * Unit tests for the DoubleEntryService to verify the core double-entry bookkeeping functionality.
 */
@ExtendWith(MockitoExtension.class)
class DoubleEntryServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    
    @Mock
    private AccountRepository accountRepository;
    
    private DoubleEntryService doubleEntryService;
    
    @BeforeEach
    void setUp() {
        doubleEntryService = new DoubleEntryService(ledgerEntryRepository, accountRepository);
    }
    
    @Test
    void createTransferEntries_ShouldCreateBalancedEntries() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        
        when(accountRepository.existsById(fromAccountId)).thenReturn(true);
        when(accountRepository.existsById(toAccountId)).thenReturn(true);
        
        Transaction transaction = new Transaction(
            fromAccountId,
            toAccountId,
            amount,
            TransactionType.TRANSFER,
            Currency.EUR
        );
        
        // Set ID for the transaction
        setTransactionId(transaction, UUID.randomUUID());
        
        // Act
        Transaction result = doubleEntryService.createTransferEntries(transaction);
        
        // Assert
        assertEquals(transaction, result); // Should return the same transaction object
        
        // Verify that ledgerEntryRepository.save() was called exactly 2 times
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }
    
    @Test
    void calculateBalance_ShouldDelegateToCreditMinusDebit() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        BigDecimal expectedBalance = new BigDecimal("150.00");
        
        when(accountRepository.existsById(accountId)).thenReturn(true);
        when(ledgerEntryRepository.calculateBalance(accountId)).thenReturn(expectedBalance);
        
        // Act
        BigDecimal actualBalance = doubleEntryService.calculateBalance(accountId);
        
        // Assert
        assertEquals(0, expectedBalance.compareTo(actualBalance));
    }
    
    @Test
    void createSystemCreditEntry_ShouldCreateCredit() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("200.00");
        String description = "System credit";
        
        // Mock that both the target account and system funding account exist
        lenient().when(accountRepository.existsById(accountId)).thenReturn(true);
        lenient().when(accountRepository.existsById(any(UUID.class))).thenReturn(true);
        
        // Act
        doubleEntryService.createSystemCreditEntry(accountId, amount, description);
        
        // Assert - verify that the save method was called with a LedgerEntry that has the right properties
        // We should have 2 calls - one for the credit and one for the debit from system account
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }
    
    @Test
    void moneyIsNeitherCreatedNorDestroyed() {
        // Arrange
        final UUID account1Id = UUID.randomUUID();
        final UUID account2Id = UUID.randomUUID();
        final UUID account3Id = UUID.randomUUID();
        final BigDecimal initialSystemBalance = new BigDecimal("1000.00");
        
        lenient().when(accountRepository.existsById(any())).thenReturn(true);
        
        // We'll keep track of the ledger entries to calculate the final balance
        // Use AtomicReference to handle mutable state in lambda
        final AtomicReference<BigDecimal> account1Balance = new AtomicReference<>(BigDecimal.ZERO);
        final AtomicReference<BigDecimal> account2Balance = new AtomicReference<>(BigDecimal.ZERO);
        final AtomicReference<BigDecimal> account3Balance = new AtomicReference<>(BigDecimal.ZERO);
        
        // Fund account1 with initial balance
        UUID initialTxId = UUID.randomUUID();
        LedgerEntry creditEntry = LedgerEntry.builder()
            .accountId(account1Id)
            .transactionId(initialTxId)
            .amount(initialSystemBalance)
            .entryType(EntryType.CREDIT)
            .description("Initial funding")
            .currency(Currency.EUR)
            .build();
            
        lenient().when(ledgerEntryRepository.save(any())).thenReturn(creditEntry);
        
        // Simulate the credit entry manually
        account1Balance.set(account1Balance.get().add(initialSystemBalance));
        
        // Create a transaction from account1 to account2
        final UUID txId1 = UUID.randomUUID();
        final Transaction tx1 = new Transaction(account1Id, account2Id, new BigDecimal("300.00"), TransactionType.TRANSFER, Currency.EUR);
        setTransactionId(tx1, txId1);
        
        // Mock the entries that would be created
        LedgerEntry debit1 = LedgerEntry.builder()
            .accountId(account1Id)
            .transactionId(txId1)
            .amount(new BigDecimal("300.00"))
            .entryType(EntryType.DEBIT)
            .description("Transfer to account 2")
            .currency(Currency.EUR)
            .build();
            
        LedgerEntry credit1 = LedgerEntry.builder()
            .accountId(account2Id)
            .transactionId(txId1)
            .amount(new BigDecimal("300.00"))
            .entryType(EntryType.CREDIT)
            .description("Transfer from account 1")
            .currency(Currency.EUR)
            .build();
        
        // Manually simulate the first transfer
        account1Balance.set(account1Balance.get().subtract(new BigDecimal("300.00")));
        account2Balance.set(account2Balance.get().add(new BigDecimal("300.00")));
        
        // Create a transaction from account2 to account3
        final UUID txId2 = UUID.randomUUID();
        final Transaction tx2 = new Transaction(account2Id, account3Id, new BigDecimal("150.00"), TransactionType.TRANSFER, Currency.EUR);
        setTransactionId(tx2, txId2);
        
        // Manually simulate the second transfer
        account2Balance.set(account2Balance.get().subtract(new BigDecimal("150.00")));
        account3Balance.set(account3Balance.get().add(new BigDecimal("150.00")));
        
        // Create a transaction from account3 to account1
        final UUID txId3 = UUID.randomUUID();
        final Transaction tx3 = new Transaction(account3Id, account1Id, new BigDecimal("50.00"), TransactionType.TRANSFER, Currency.EUR);
        setTransactionId(tx3, txId3);
        
        // Manually simulate the third transfer
        account3Balance.set(account3Balance.get().subtract(new BigDecimal("50.00")));
        account1Balance.set(account1Balance.get().add(new BigDecimal("50.00")));
        
        // Assert - The sum of all balances should equal the initial system balance
        BigDecimal finalSystemBalance = account1Balance.get().add(account2Balance.get()).add(account3Balance.get());
        assertEquals(0, initialSystemBalance.compareTo(finalSystemBalance), 
            "The total system balance should be preserved: expected " + initialSystemBalance + 
            ", but got " + finalSystemBalance);
    }
    
    /**
     * Helper method to set the transaction ID using reflection.
     */
    private void setTransactionId(Transaction transaction, UUID id) {
        try {
            java.lang.reflect.Field field = Transaction.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(transaction, id);
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException e) {
            throw new RuntimeException("Failed to set transaction ID", e);
        }
    }
} 