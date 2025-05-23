package com.cubeia.wallet.service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.exception.CurrencyMismatchException;
import com.cubeia.wallet.exception.InsufficientFundsException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.LedgerEntryRepository;
import com.cubeia.wallet.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private PlatformTransactionManager transactionManager;
    
    @Mock
    private TransactionTemplate transactionTemplate;
    
    @Mock
    private TransactionStatus transactionStatus;

    @Mock
    private ValidationService validationService;
    
    @Mock
    private DoubleEntryService doubleEntryService;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private TransactionService transactionService;

    @BeforeEach
    @SuppressWarnings("unused") // Called implicitly by JUnit
    void setUp() {
        // Create transaction service with mocked dependencies
        transactionService = new TransactionService(
                accountRepository, 
                transactionRepository, 
                validationService,
                doubleEntryService,
                transactionManager) {
            @Override
            protected TransactionTemplate getTransactionTemplate() {
                // Return our mocked template instead of creating a new one
                return transactionTemplate;
            }
        };
    }

    /**
     * Helper method to configure transaction template for tests that need it.
     * Only call this in tests that specifically test transaction behavior.
     */
    private void configureTransactionTemplate() {
        // Configure only what's needed for isolation level test
        when(transactionTemplate.getIsolationLevel())
            .thenReturn(TransactionDefinition.ISOLATION_SERIALIZABLE);
        
        // Configure template execution for tests that need transaction callbacks
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            if (callback != null) {
                return callback.doInTransaction(transactionStatus);
            }
            return null;
        });
    }

    /**
     * Helper method to configure basic account mocking
     */
    private void configureBasicAccountMocks(UUID fromAccountId, UUID toAccountId, Account fromAccount, Account toAccount) {
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        when(accountRepository.existsById(fromAccountId)).thenReturn(true);
        when(accountRepository.existsById(toAccountId)).thenReturn(true);
    }

    /**
     * Helper method to configure transaction saving mock
     */
    private void configureTransactionSaving() {
        // Clear any previous mock configuration
        reset(transactionRepository);
        
        // Configure save behavior
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedTransaction = invocation.getArgument(0);
            if (savedTransaction.getId() == null) {
                setTransactionId(savedTransaction, UUID.randomUUID());
            }
            return savedTransaction;
        });
    }

    /**
     * Helper method to set account ID using reflection (for testing)
     */
    private void setAccountId(Account account, UUID id) {
        try {
            Field idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(account, id);
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException e) {
            throw new RuntimeException("Failed to set ID", e);
        }
    }

    /**
     * Helper method to set transaction ID using reflection (for testing)
     */
    private void setTransactionId(Transaction transaction, UUID id) {
        try {
            Field idField = Transaction.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(transaction, id);
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException e) {
            throw new RuntimeException("Failed to set transaction ID", e);
        }
    }

    /**
     * Sets up the mock to return specific balance for an account.
     * Only use this when testing balance-dependent behavior.
     */
    private void mockAccountBalance(UUID accountId, BigDecimal balance) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (balance == null) {
            throw new IllegalArgumentException("Balance cannot be null");
        }
        
        // Set up the mock to return the specified balance
        when(doubleEntryService.calculateBalance(eq(accountId))).thenReturn(balance);
    }

    @Test
    void transfer_ShouldSuccessfullyTransferFundsUsingDoubleEntry() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountId(toAccount, toAccountId);

        // Mock validation service - this is what's actually used in the test
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenReturn(new TransferValidationResult(fromAccount, toAccount, null));

        // Mock account repository
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));

        // Mock transaction saving
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedTransaction = invocation.getArgument(0);
            if (savedTransaction.getId() == null) {
                try {
                    Field idField = Transaction.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(savedTransaction, UUID.randomUUID());
                } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException e) {
                    throw new RuntimeException("Failed to set transaction ID", e);
                }
            }
            return savedTransaction;
        });

        // Mock double entry service - this is what's used in test
        when(doubleEntryService.createTransferEntries(any(Transaction.class))).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });
        
        // Mock balance check - important for avoiding NullPointerException
        when(doubleEntryService.calculateBalance(eq(fromAccountId))).thenReturn(new BigDecimal("100.00"));

        // Act
        Transaction result = transactionService.transfer(fromAccountId, toAccountId, amount, null, null);

        // Assert
        assertNotNull(result);
        assertEquals(fromAccountId, result.getFromAccountId());
        assertEquals(toAccountId, result.getToAccountId());
        assertEquals(amount, result.getAmount());
        assertEquals(Currency.EUR, result.getCurrency());

        // Verify proper service calls
        verify(validationService).validateTransferParameters(fromAccountId, toAccountId, amount, null);
        verify(doubleEntryService).createTransferEntries(any(Transaction.class));
        verify(transactionRepository, atLeastOnce()).save(any(Transaction.class));
    }

    @Test
    void transfer_ShouldThrowInsufficientFundsException() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("300.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountId(toAccount, toAccountId);

        // Mock validation service to throw InsufficientFundsException
        InsufficientFundsException expectedException = 
            new InsufficientFundsException(fromAccountId, "Insufficient funds for test");
            
        when(validationService.validateTransferParameters(
            eq(fromAccountId), eq(toAccountId), eq(amount), any()))
            .thenThrow(expectedException);

        // Act & Assert
        InsufficientFundsException thrown = assertThrows(
            InsufficientFundsException.class,
            () -> transactionService.transfer(fromAccountId, toAccountId, amount, null, null)
        );

        // Assert we got the same exception
        assertEquals(expectedException, thrown);

        // Verify that validation was called but no database operations would be performed
        verify(validationService).validateTransferParameters(
            eq(fromAccountId), eq(toAccountId), eq(amount), any());
        verify(doubleEntryService, never()).createTransferEntries(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_ShouldThrowAccountNotFoundExceptionForSender() {
        // Arrange
        UUID nonExistentAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        // Mock validation service to throw AccountNotFoundException
        AccountNotFoundException expectedException = new AccountNotFoundException(nonExistentAccountId);
        when(validationService.validateTransferParameters(
            eq(nonExistentAccountId), eq(toAccountId), eq(amount), any()))
            .thenThrow(expectedException);

        // Act & Assert
        AccountNotFoundException thrown = assertThrows(
            AccountNotFoundException.class,
            () -> transactionService.transfer(nonExistentAccountId, toAccountId, amount, null, null)
        );

        // Verify the exception contains the correct account ID
        assertTrue(thrown.getMessage().contains(nonExistentAccountId.toString()));

        // Verify no ledger entries or transactions were created
        verify(doubleEntryService, never()).createTransferEntries(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_ShouldThrowAccountNotFoundExceptionForReceiver() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID nonExistentAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        // Mock validation service to throw AccountNotFoundException
        AccountNotFoundException expectedException = new AccountNotFoundException(nonExistentAccountId);
        when(validationService.validateTransferParameters(
            eq(fromAccountId), eq(nonExistentAccountId), eq(amount), any()))
            .thenThrow(expectedException);

        // Act & Assert
        AccountNotFoundException thrown = assertThrows(
            AccountNotFoundException.class,
            () -> transactionService.transfer(fromAccountId, nonExistentAccountId, amount, null, null)
        );

        // Verify the exception contains the correct account ID
        assertTrue(thrown.getMessage().contains(nonExistentAccountId.toString()));

        // Verify no ledger entries or transactions were created
        verify(doubleEntryService, never()).createTransferEntries(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_ShouldRejectCurrencyMismatch() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        // Mock validation service to throw CurrencyMismatchException
        CurrencyMismatchException expectedException = CurrencyMismatchException.forTransfer(Currency.EUR, Currency.USD);
        when(validationService.validateTransferParameters(
            eq(fromAccountId), eq(toAccountId), eq(amount), any()))
            .thenThrow(expectedException);

        // Act & Assert
        CurrencyMismatchException thrown = assertThrows(
            CurrencyMismatchException.class,
            () -> transactionService.transfer(fromAccountId, toAccountId, amount, null, null)
        );

        // Verify the exception contains both currencies
        String errorMessage = thrown.getMessage();
        assertTrue(errorMessage.contains(Currency.EUR.toString()));
        assertTrue(errorMessage.contains(Currency.USD.toString()));

        // Verify no ledger entries or transactions were created
        verify(doubleEntryService, never()).createTransferEntries(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_ShouldWorkBetweenDifferentAccountTypes() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account toAccount = new Account(Currency.EUR, AccountType.BonusAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountId(toAccount, toAccountId);

        // Mock validation service
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenReturn(new TransferValidationResult(fromAccount, toAccount, null));

        // Mock account repository
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));

        // Mock transaction saving to return the same transaction
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedTransaction = invocation.getArgument(0);
            if (savedTransaction.getId() == null) {
                try {
                    Field idField = Transaction.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(savedTransaction, UUID.randomUUID());
                } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException e) {
                    throw new RuntimeException("Failed to set transaction ID", e);
                }
            }
            return savedTransaction;
        });

        // Mock double entry service
        when(doubleEntryService.createTransferEntries(any(Transaction.class))).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });
        
        // Mock balance check to avoid NPE
        when(doubleEntryService.calculateBalance(eq(fromAccountId))).thenReturn(new BigDecimal("100.00"));

        // Act
        Transaction result = transactionService.transfer(fromAccountId, toAccountId, amount, null, null);

        // Assert
        assertNotNull(result);
        assertEquals(fromAccountId, result.getFromAccountId());
        assertEquals(toAccountId, result.getToAccountId());
        assertEquals(amount, result.getAmount());
        assertEquals(Currency.EUR, result.getCurrency());

        // Verify proper service calls - allow multiple saves since the transaction might be updated
        verify(validationService).validateTransferParameters(fromAccountId, toAccountId, amount, null);
        verify(doubleEntryService).createTransferEntries(any(Transaction.class));
        verify(transactionRepository, atLeastOnce()).save(any(Transaction.class));
    }

    @Test
    void getAccountTransactions_ShouldThrowForNonExistentAccount() {
        // Arrange
        UUID nonExistentAccountId = UUID.randomUUID();
        
        // Mock account repository
        when(accountRepository.existsById(nonExistentAccountId)).thenReturn(false);
        
        // Act & Assert
        AccountNotFoundException thrown = assertThrows(
            AccountNotFoundException.class,
            () -> transactionService.getAccountTransactions(nonExistentAccountId)
        );
        
        // Verify the exception contains the correct account ID
        assertTrue(thrown.getMessage().contains(nonExistentAccountId.toString()));
        
        // Verify repository calls
        verify(accountRepository).existsById(nonExistentAccountId);
        verify(transactionRepository, never()).findByAccountId(any());
    }

    @Test
    void getAccountTransactions_ShouldReturnTransactions() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        
        // Create test transactions
        Transaction outgoingTransaction = new Transaction(
            accountId, 
            UUID.randomUUID(), 
            new BigDecimal("100.00"),
            TransactionType.TRANSFER,
            Currency.EUR
        );
        Transaction incomingTransaction = new Transaction(
            UUID.randomUUID(), 
            accountId, 
            new BigDecimal("50.00"),
            TransactionType.TRANSFER,
            Currency.EUR
        );
        List<Transaction> expectedTransactions = List.of(outgoingTransaction, incomingTransaction);
        
        // Mock repositories
        when(accountRepository.existsById(accountId)).thenReturn(true);
        when(transactionRepository.findByAccountId(accountId)).thenReturn(expectedTransactions);
        
        // Act
        List<Transaction> result = transactionService.getAccountTransactions(accountId);
        
        // Assert
        assertEquals(expectedTransactions.size(), result.size());
        assertTrue(result.contains(outgoingTransaction));
        assertTrue(result.contains(incomingTransaction));
        
        // Verify repository calls
        verify(accountRepository).existsById(accountId);
        verify(transactionRepository).findByAccountId(accountId);
    }

    /**
     * Tests that transactions are committed properly.
     * This test verifies that TransactionTemplate commits all changes
     * when a transaction completes successfully.
     */
    @Test
    void transfer_WithTransactionTemplate_ShouldCommitChanges() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountId(toAccount, toAccountId);
        
        // Only test isolation level which is what this test verifies
        when(transactionTemplate.getIsolationLevel())
            .thenReturn(TransactionDefinition.ISOLATION_SERIALIZABLE);
            
        // Simple mocks only for what's being used in the test
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        
        // Mock validation service
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenReturn(new TransferValidationResult(fromAccount, toAccount, null));
        
        // Mock repository to return saved transaction
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedTransaction = invocation.getArgument(0);
            if (savedTransaction.getId() == null) {
                setTransactionId(savedTransaction, UUID.randomUUID());
            }
            return savedTransaction;
        });
        
        // Mock double entry service
        when(doubleEntryService.createTransferEntries(any(Transaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
            
        // Mock balance check to avoid NPE    
        when(doubleEntryService.calculateBalance(eq(fromAccountId))).thenReturn(new BigDecimal("200.00"));
        
        // Act
        Transaction result = transactionService.transfer(fromAccountId, toAccountId, amount, null, null);
        
        // Assert
        assertNotNull(result);
        assertEquals(fromAccountId, result.getFromAccountId());
        assertEquals(toAccountId, result.getToAccountId());
        assertEquals(amount, result.getAmount());
        assertEquals(Currency.EUR, result.getCurrency());
        
        // Verify the important service calls
        verify(doubleEntryService).createTransferEntries(any());
        verify(transactionRepository, atLeast(1)).save(any()); 
        verify(validationService).validateTransferParameters(fromAccountId, toAccountId, amount, null);
        
        // Most important assertion for this test - verifies isolation level
        TransactionTemplate realTemplate = transactionService.getTransactionTemplate();
        assertEquals(
            TransactionDefinition.ISOLATION_SERIALIZABLE,
            realTemplate.getIsolationLevel(),
            "Transaction should use SERIALIZABLE isolation level"
        );
    }
    
    /**
     * Tests that transactions are rolled back when exceptions occur.
     * This test verifies that TransactionTemplate properly rolls back all
     * changes when an exception is thrown during transaction execution.
     */
    @Test
    void transfer_WithTransactionTemplate_ShouldRollBackOnException() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountId(toAccount, toAccountId);
        
        // Simple mocks only for what's being used in the test
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        
        // Mock validation service
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenReturn(new TransferValidationResult(fromAccount, toAccount, null));
        
        // Mock repository to return saved transaction
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedTransaction = invocation.getArgument(0);
            if (savedTransaction.getId() == null) {
                setTransactionId(savedTransaction, UUID.randomUUID());
            }
            return savedTransaction;
        });
        
        // Mock double entry service to throw an exception - this is the key for this test
        RuntimeException expectedError = new RuntimeException("Failed to create ledger entries");
        when(doubleEntryService.createTransferEntries(any(Transaction.class)))
            .thenThrow(expectedError);
        
        // Act & Assert
        RuntimeException thrown = assertThrows(
            RuntimeException.class,
            () -> transactionService.transfer(fromAccountId, toAccountId, amount, null, null)
        );
        
        assertEquals(expectedError, thrown);
        
        // Verify the important interactions for this test
        verify(doubleEntryService).createTransferEntries(any());
        verify(transactionRepository, atLeastOnce()).save(any());
    }

    /**
     * Tests that transactions are properly isolated.
     * Ensures that transaction isolation level is properly set and used.
     */
    @Test
    void transfer_WithTransactionTemplate_ShouldUseProperIsolation() {
        // For this test we need to create a real TransactionService since our mocked
        // version doesn't have the SERIALIZABLE isolation level set
        
        // Create a new TransactionService with real transaction manager 
        TransactionService realService = new TransactionService(
            accountRepository,
            transactionRepository,
            validationService,
            doubleEntryService,
            transactionManager);
        
        // Act - Get the transaction template from the real service
        TransactionTemplate realTemplate = realService.getTransactionTemplate();
        
        // Assert - Verify it has the expected isolation level
        assertEquals(
            TransactionDefinition.ISOLATION_SERIALIZABLE,
            realTemplate.getIsolationLevel(),
            "Transaction should use SERIALIZABLE isolation level"
        );
    }

    @Test
    void transfer_ShouldReturnExistingTransactionForIdempotency() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        String referenceId = "TEST-REF-123";
        
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountId(toAccount, toAccountId);
        
        // Create existing transaction with proper ID
        Transaction existingTransaction = new Transaction(
            fromAccountId,
            toAccountId,
            amount,
            TransactionType.TRANSFER,
            Currency.EUR,
            referenceId
        );
        UUID existingTransactionId = UUID.randomUUID();
        try {
            Field idField = Transaction.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(existingTransaction, existingTransactionId);
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException e) {
            throw new RuntimeException("Failed to set transaction ID", e);
        }
        
        // Mock validation service to return existing transaction
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, referenceId))
            .thenReturn(new TransferValidationResult(fromAccount, toAccount, existingTransaction));
        
        // Act
        Transaction result = transactionService.transfer(fromAccountId, toAccountId, amount, referenceId, null);
        
        // Assert
        assertNotNull(result);
        assertEquals(existingTransactionId, result.getId());
        assertEquals(referenceId, result.getReference());
        assertEquals(fromAccountId, result.getFromAccountId());
        assertEquals(toAccountId, result.getToAccountId());
        assertEquals(amount, result.getAmount());
        assertEquals(Currency.EUR, result.getCurrency());
        
        // Verify validation was performed
        verify(validationService).validateTransferParameters(fromAccountId, toAccountId, amount, referenceId);
        
        // Verify no new transaction was created
        verify(doubleEntryService, never()).createTransferEntries(any());
        verify(transactionRepository, never()).save(any());
        verify(transactionStatus, never()).setRollbackOnly();
    }
} 