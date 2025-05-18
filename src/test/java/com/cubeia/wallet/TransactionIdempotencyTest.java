package com.cubeia.wallet;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.TransactionRepository;
import com.cubeia.wallet.service.DoubleEntryService;
import com.cubeia.wallet.service.TransactionService;
import com.cubeia.wallet.service.ValidationService;

/**
 * Tests for verifying idempotent behavior of transactions with reference IDs.
 * These tests ensure that transactions with the same reference ID are only executed once,
 * which is critical for financial systems to prevent duplicate transactions.
 */
class TransactionIdempotencyTest {

    @Mock
    private AccountRepository accountRepository;
    
    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private DoubleEntryService doubleEntryService;
    
    @Mock
    private ValidationService validationService;
    
    @Mock
    private PlatformTransactionManager transactionManager;
    
    private TransactionService transactionService;
    
    private Account sourceAccount;
    private Account destinationAccount;
    private final BigDecimal initialBalance = new BigDecimal("1000.00");
    private final BigDecimal transferAmount = new BigDecimal("100.00");
    
    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        
        // Create accounts
        sourceAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setField(sourceAccount, "id", UUID.randomUUID());
        
        destinationAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setField(destinationAccount, "id", UUID.randomUUID());
        
        // Setup mocks
        when(accountRepository.findById(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccount.getId())).thenReturn(Optional.of(destinationAccount));
        
        // Mock balance calculations via DoubleEntryService instead of injecting it into Account
        when(doubleEntryService.calculateBalance(sourceAccount.getId())).thenReturn(initialBalance);
        when(doubleEntryService.calculateBalance(destinationAccount.getId())).thenReturn(BigDecimal.ZERO);
        
        // Setup transaction service with custom transaction template
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager) {
            @Override
            public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                // Provide a dummy TransactionStatus to avoid NPE
                org.springframework.transaction.TransactionStatus dummyStatus = org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class);
                org.mockito.Mockito.doNothing().when(dummyStatus).setRollbackOnly();
                return action.doInTransaction(dummyStatus);
            }
        };
        
        // Create the TransactionService with the correct constructor order
        transactionService = new TransactionService(
            accountRepository, 
            transactionRepository, 
            validationService,
            doubleEntryService,  // Correct position for doubleEntryService
            transactionManager
        ) {
            // Override the transaction template method if it exists in the implementation
            // Otherwise, this is a test-specific extension
            @Override
            protected TransactionTemplate getTransactionTemplate() {
                return transactionTemplate;
            }
        };
    }
    
    /**
     * Test that a transaction with a reference ID is successfully processed the first time.
     */
    @Test
    public void testFirstTransactionWithReferenceId() {
        // Setup validation to pass
        ValidationService.TransferValidationResult validationResult =
            new ValidationService.TransferValidationResult(sourceAccount, destinationAccount, null);
        when(validationService.validateTransferParameters(
            sourceAccount.getId(), destinationAccount.getId(), transferAmount, "TEST-REF-001"))
            .thenReturn(validationResult);
        
        // Setup transaction repository to save and return transaction
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            if (tx.getId() == null) {
                setField(tx, "id", UUID.randomUUID());
            }
            return tx;
        });
        
        // Stub the findByReference to return empty (no existing transaction)
        when(transactionRepository.findByReferenceIgnoreCase("TEST-REF-001")).thenReturn(Optional.empty());
        
        // Execute test
        Transaction transaction = transactionService.transfer(
                sourceAccount.getId(),
                destinationAccount.getId(),
                transferAmount,
                "TEST-REF-001",
                null
        );
        
        // Verify
        assertNotNull(transaction, "Transaction should be created");
        assertEquals("TEST-REF-001", transaction.getReference(), "Transaction should have the correct reference ID");
        
        // Verify the transactions were created with the right parameters
        verify(doubleEntryService).createTransferEntries(any(Transaction.class));
        verify(transactionRepository, atLeast(1)).save(any(Transaction.class));
    }
    
    /**
     * Test that attempting to process a transaction with the same reference ID twice
     * returns the original transaction and doesn't modify account balances again.
     */
    @Test
    public void testDuplicateTransactionWithSameReferenceId() {
        // Create an existing transaction
        Transaction existingTransaction = new Transaction(
            sourceAccount.getId(),
            destinationAccount.getId(),
            transferAmount,
            TransactionType.TRANSFER,
            Currency.EUR,
            "TEST-REF-002"
        );
        setField(existingTransaction, "id", UUID.randomUUID());
        
        // Setup validation to return the existing transaction 
        ValidationService.TransferValidationResult validationResult =
            new ValidationService.TransferValidationResult(sourceAccount, destinationAccount, existingTransaction);
        when(validationService.validateTransferParameters(
            sourceAccount.getId(), destinationAccount.getId(), transferAmount, "TEST-REF-002"))
            .thenReturn(validationResult);
            
        // Execute the transfer
        Transaction secondTransaction = transactionService.transfer(
                sourceAccount.getId(),
                destinationAccount.getId(),
                transferAmount,
                "TEST-REF-002",
                null
        );
        
        // The second call should return the existing transaction without creating a new one
        assertNotNull(secondTransaction, "Transaction should be returned");
        assertEquals(existingTransaction.getId(), secondTransaction.getId(), 
                "The same transaction should be returned for duplicate reference IDs");
        
        // Verify that doubleEntryService.createTransferEntries was NOT called again
        verify(doubleEntryService, never()).createTransferEntries(any(Transaction.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
    
    /**
     * Test behavior with null reference IDs.
     */
    @Test
    public void testTransactionsWithNullReferenceId() {
        // Setup test transactions
        Transaction firstTransaction = new Transaction(
            sourceAccount.getId(),
            destinationAccount.getId(),
            transferAmount,
            TransactionType.TRANSFER,
            Currency.EUR
        );
        setField(firstTransaction, "id", UUID.randomUUID());
        
        Transaction secondTransaction = new Transaction(
            sourceAccount.getId(),
            destinationAccount.getId(),
            transferAmount,
            TransactionType.TRANSFER,
            Currency.EUR
        );
        setField(secondTransaction, "id", UUID.randomUUID());
        
        // Setup validation to pass
        ValidationService.TransferValidationResult validationResult =
            new ValidationService.TransferValidationResult(sourceAccount, destinationAccount, null);
        when(validationService.validateTransferParameters(
                eq(sourceAccount.getId()), eq(destinationAccount.getId()), eq(transferAmount), isNull()))
                .thenReturn(validationResult);
        
        // Setup transaction repository to return distinct transactions
        when(transactionRepository.save(any())).thenReturn(firstTransaction, secondTransaction);
        
        // First transaction with null reference ID
        Transaction firstResult = transactionService.transfer(
                sourceAccount.getId(),
                destinationAccount.getId(),
                transferAmount,
                null,
                null
        );
        
        // Second transaction with null reference ID
        Transaction secondResult = transactionService.transfer(
                sourceAccount.getId(),
                destinationAccount.getId(),
                transferAmount,
                null,
                null
        );
        
        // Both transactions should be processed separately
        assertNotNull(firstResult, "First transaction should be created");
        assertNotNull(secondResult, "Second transaction should be created");
        
        // Different transactions should have different IDs
        assertNotEquals(firstResult.getId(), secondResult.getId(),
                "Transactions should have different IDs");
    }
    
    /**
     * Helper method to set a private field via reflection
     */
    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
} 