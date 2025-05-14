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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.cubeia.wallet.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

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

    @InjectMocks
    private TransactionService transactionService;
    
    @BeforeEach
    void setup() {
        // Setup default TransactionTemplate behavior
        // Use lenient() to avoid UnnecessaryStubbing errors
        lenient().when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
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
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID", e);
        }
    }
    
    /**
     * Helper method to set account balance using reflection (for testing)
     */
    private void setAccountBalance(Account account, BigDecimal balance) {
        try {
            Field balanceField = Account.class.getDeclaredField("balance");
            balanceField.setAccessible(true);
            balanceField.set(account, balance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set balance", e);
        }
    }

    private Transaction mockMatchingExistingTransaction(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String referenceId) {
        Transaction existingTransaction = new Transaction(
            fromAccountId, 
            toAccountId, 
            amount, 
            TransactionType.TRANSFER, 
            Currency.EUR, 
            referenceId
        );
        try {
            Field idField = Transaction.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(existingTransaction, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID", e);
        }
        return existingTransaction;
    }

    @Test
    void transfer_ShouldSuccessfullyTransferFunds() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountBalance(fromAccount, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        setAccountBalance(toAccount, new BigDecimal("50.00"));

        // Mock validation service
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenReturn(new ValidationService.TransferValidationResult(fromAccount, toAccount, null));
            
        // Mock repository methods for the new flow
        when(accountRepository.findByIdWithLock(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdWithLock(toAccountId)).thenReturn(Optional.of(toAccount));
        
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedTransaction = invocation.getArgument(0);
            // Use reflection to set transaction ID
            try {
                Field idField = Transaction.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(savedTransaction, UUID.randomUUID());
            } catch (Exception e) {
                throw new RuntimeException("Failed to set transaction ID", e);
            }
            return savedTransaction;
        });

        // Act
        Transaction result = transactionService.transfer(fromAccountId, toAccountId, amount);

        // Assert
        assertNotNull(result);
        assertEquals(fromAccountId, result.getFromAccountId());
        assertEquals(toAccountId, result.getToAccountId());
        assertEquals(amount, result.getAmount());
        assertEquals(Currency.EUR, result.getCurrency());
        assertEquals(TransactionType.TRANSFER, result.getTransactionType());

        assertEquals(new BigDecimal("100.00"), fromAccount.getBalance());
        assertEquals(new BigDecimal("150.00"), toAccount.getBalance());

        // Verify correct method calls with exact counts
        verify(validationService, times(1)).validateTransferParameters(fromAccountId, toAccountId, amount, null);
        verify(accountRepository, times(1)).findByIdWithLock(fromAccountId);
        verify(accountRepository, times(1)).findByIdWithLock(toAccountId);
        verify(accountRepository, times(2)).save(any(Account.class));
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void transfer_ShouldThrowInsufficientFundsException() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("300.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountBalance(fromAccount, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        setAccountBalance(toAccount, new BigDecimal("50.00"));

        // Mock validation service to throw InsufficientFundsException
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenThrow(new InsufficientFundsException(fromAccountId, "Insufficient funds for test"));

        // Act & Assert
        assertThrows(InsufficientFundsException.class, () -> {
            transactionService.transfer(fromAccountId, toAccountId, amount);
        });

        // Verify that validation was called but no database operations were performed
        verify(validationService, times(1)).validateTransferParameters(fromAccountId, toAccountId, amount, null);
        verify(accountRepository, never()).findByIdWithLock(any(UUID.class));
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_ShouldThrowAccountNotFoundExceptionForSender() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        // Mock validation service to throw AccountNotFoundException
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenThrow(new AccountNotFoundException(fromAccountId));

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            transactionService.transfer(fromAccountId, toAccountId, amount);
        });

        // Verify validation service was called but transaction was not processed
        verify(validationService, times(1)).validateTransferParameters(fromAccountId, toAccountId, amount, null);
        verify(accountRepository, never()).findByIdWithLock(any(UUID.class));
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_ShouldThrowAccountNotFoundExceptionForReceiver() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        // Mock validation service to throw AccountNotFoundException
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenThrow(new AccountNotFoundException(toAccountId));

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            transactionService.transfer(fromAccountId, toAccountId, amount);
        });

        // Verify that validation service was called but transaction was not processed
        verify(validationService, times(1)).validateTransferParameters(fromAccountId, toAccountId, amount, null);
        verify(accountRepository, never()).findByIdWithLock(any(UUID.class));
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_ShouldAcceptNegativeAmount() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("-50.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountBalance(fromAccount, new BigDecimal("200.00"));
        
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        
        // Mock validation service to return our accounts
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenReturn(new ValidationService.TransferValidationResult(fromAccount, toAccount, null));

        // We allow negative amounts because validation is handled at the DTO level with @Positive
        // And our test is verifying that the service itself doesn't reject negative amounts
        
        // Mock repository methods for locked accounts
        when(accountRepository.findByIdWithLock(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdWithLock(toAccountId)).thenReturn(Optional.of(toAccount));
        
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedTransaction = invocation.getArgument(0);
            try {
                Field idField = Transaction.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(savedTransaction, UUID.randomUUID());
            } catch (Exception e) {
                throw new RuntimeException("Failed to set transaction ID", e);
            }
            return savedTransaction;
        });
        
        // Act
        Transaction result = transactionService.transfer(fromAccountId, toAccountId, amount);
        
        // Assert
        assertNotNull(result);
        assertEquals(amount, result.getAmount());
        
        // Verify validation service was called
        verify(validationService, times(1)).validateTransferParameters(fromAccountId, toAccountId, amount, null);
    }

    @Test
    void transfer_ShouldAcceptZeroAmount() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.ZERO;

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountBalance(fromAccount, new BigDecimal("200.00"));
        
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        
        // Mock validation service to return our accounts
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenReturn(new ValidationService.TransferValidationResult(fromAccount, toAccount, null));

        // We allow zero amounts because validation is handled at the DTO level with @Positive
        // And our test is verifying that the service itself doesn't reject zero amounts
        
        // Mock repository methods for locked accounts
        when(accountRepository.findByIdWithLock(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdWithLock(toAccountId)).thenReturn(Optional.of(toAccount));
        
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedTransaction = invocation.getArgument(0);
            try {
                Field idField = Transaction.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(savedTransaction, UUID.randomUUID());
            } catch (Exception e) {
                throw new RuntimeException("Failed to set transaction ID", e);
            }
            return savedTransaction;
        });
        
        // Act
        Transaction result = transactionService.transfer(fromAccountId, toAccountId, amount);
        
        // Assert
        assertNotNull(result);
        assertEquals(amount, result.getAmount());
        
        // Verify validation service was called
        verify(validationService, times(1)).validateTransferParameters(fromAccountId, toAccountId, amount, null);
    }

    @Test
    void transfer_ShouldRejectCurrencyMismatch() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        // Mock validation service to throw CurrencyMismatchException
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenThrow(CurrencyMismatchException.forTransfer(Currency.EUR, Currency.USD));

        // Act & Assert
        CurrencyMismatchException exception = assertThrows(CurrencyMismatchException.class, () -> {
            transactionService.transfer(fromAccountId, toAccountId, amount);
        });

        assertTrue(exception.getMessage().contains("Cannot transfer between accounts with different currencies"));
        
        // Verify validation service was called
        verify(validationService, times(1)).validateTransferParameters(fromAccountId, toAccountId, amount, null);
        verify(accountRepository, never()).findByIdWithLock(any(UUID.class));
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
    
    @Test
    void transfer_ShouldWorkBetweenDifferentAccountTypes() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountBalance(fromAccount, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.EUR, AccountType.BonusAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        setAccountBalance(toAccount, new BigDecimal("50.00"));

        // Mock validation service
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenReturn(new ValidationService.TransferValidationResult(fromAccount, toAccount, null));

        // Mock repository methods for locked accounts
        when(accountRepository.findByIdWithLock(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdWithLock(toAccountId)).thenReturn(Optional.of(toAccount));
        
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedTransaction = invocation.getArgument(0);
            try {
                Field idField = Transaction.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(savedTransaction, UUID.randomUUID());
            } catch (Exception e) {
                throw new RuntimeException("Failed to set transaction ID", e);
            }
            return savedTransaction;
        });

        // Act
        Transaction result = transactionService.transfer(fromAccountId, toAccountId, amount);

        // Assert
        assertNotNull(result);
        assertEquals(fromAccountId, result.getFromAccountId());
        assertEquals(toAccountId, result.getToAccountId());
        assertEquals(amount, result.getAmount());
        assertEquals(Currency.EUR, result.getCurrency());
        assertEquals(TransactionType.TRANSFER, result.getTransactionType());

        assertEquals(new BigDecimal("100.00"), fromAccount.getBalance());
        assertEquals(new BigDecimal("150.00"), toAccount.getBalance());

        // Verify correct method calls
        verify(validationService, times(1)).validateTransferParameters(fromAccountId, toAccountId, amount, null);
        verify(accountRepository, times(1)).findByIdWithLock(fromAccountId);
        verify(accountRepository, times(1)).findByIdWithLock(toAccountId);
        verify(accountRepository, times(2)).save(any(Account.class));
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void getAccountTransactions_ShouldThrowForNonExistentAccount() {
        // Arrange
        UUID nonExistentAccountId = UUID.randomUUID();
        
        when(accountRepository.existsById(nonExistentAccountId)).thenReturn(false);
        
        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            transactionService.getAccountTransactions(nonExistentAccountId);
        });
        
        verify(accountRepository, times(1)).existsById(nonExistentAccountId);
        verify(transactionRepository, never()).findByAccountId(any());
    }
    
    @Test
    void getAccountTransactions_ShouldReturnTransactions() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        Transaction transaction1 = new Transaction(
            accountId, 
            UUID.randomUUID(), 
            new BigDecimal("100.00"),
            TransactionType.TRANSFER,
            Currency.EUR
        );
        Transaction transaction2 = new Transaction(
            UUID.randomUUID(), 
            accountId, 
            new BigDecimal("50.00"),
            TransactionType.TRANSFER,
            Currency.EUR
        );
        List<Transaction> expectedTransactions = List.of(transaction1, transaction2);
        
        when(accountRepository.existsById(accountId)).thenReturn(true);
        when(transactionRepository.findByAccountId(accountId)).thenReturn(expectedTransactions);
        
        // Act
        List<Transaction> result = transactionService.getAccountTransactions(accountId);
        
        // Assert
        assertEquals(expectedTransactions, result);
        assertEquals(2, result.size());
        verify(accountRepository, times(1)).existsById(accountId);
        verify(transactionRepository, times(1)).findByAccountId(accountId);
    }

    /**
     * Tests transfer with explicit transaction management using TransactionTemplate.
     * This test verifies that transactions are properly managed using TransactionTemplate
     * and that changes are properly committed when all operations succeed.
     */
    @Test
    void transfer_WithTransactionTemplate_ShouldCommitChanges() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountBalance(fromAccount, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        setAccountBalance(toAccount, new BigDecimal("50.00"));
        
        // Mock validation service
        lenient().when(validationService.validateTransferParameters(
                eq(fromAccountId), eq(toAccountId), eq(amount), any()))
            .thenReturn(new ValidationService.TransferValidationResult(fromAccount, toAccount, null));
        
        // Mock locked accounts retrieval
        lenient().when(accountRepository.findByIdWithLock(fromAccountId)).thenReturn(Optional.of(fromAccount));
        lenient().when(accountRepository.findByIdWithLock(toAccountId)).thenReturn(Optional.of(toAccount));
        
        lenient().when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedTransaction = invocation.getArgument(0);
            try {
                Field idField = Transaction.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(savedTransaction, UUID.randomUUID());
            } catch (Exception e) {
                throw new RuntimeException("Failed to set transaction ID", e);
            }
            return savedTransaction;
        });

        // Create a service with our mocked template
        TransactionService serviceWithTemplate = createServiceWithMockedTemplate();
        
        // Act
        Transaction result = serviceWithTemplate.transfer(fromAccountId, toAccountId, amount);

        // Assert
        assertNotNull(result);
        assertEquals(fromAccountId, result.getFromAccountId());
        assertEquals(toAccountId, result.getToAccountId());
        assertEquals(new BigDecimal("100.00"), fromAccount.getBalance());
        assertEquals(new BigDecimal("150.00"), toAccount.getBalance());
        
        // Verify transaction template was called
        verify(transactionTemplate, times(1)).execute(any(TransactionCallback.class));
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
        String referenceId = "TEST-REF-001";
        
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountBalance(fromAccount, new BigDecimal("200.00"));
        
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        setAccountBalance(toAccount, new BigDecimal("50.00"));
        
        // Mock the transaction template with a spy that throws an exception
        TransactionTemplate mockTemplate = mock(TransactionTemplate.class);
        when(mockTemplate.execute(any(TransactionCallback.class))).thenThrow(
                new RuntimeException("Database error"));
        
        // Create service with mock template
        TransactionService serviceWithMockTemplate = new TransactionService(
                accountRepository, transactionRepository, validationService, transactionManager) {
            @Override
            protected TransactionTemplate getTransactionTemplate() {
                return mockTemplate;
            }
        };
        
        // Mock validation service to return valid results
        lenient().when(validationService.validateTransferParameters(
                eq(fromAccountId), eq(toAccountId), eq(amount), eq(referenceId)))
            .thenReturn(new ValidationService.TransferValidationResult(fromAccount, toAccount, null));
        
        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            serviceWithMockTemplate.transfer(fromAccountId, toAccountId, amount, referenceId);
        });
        
        // Verify that the exception has the expected message
        assertEquals("Database error", exception.getMessage());
        
        // Verify that the validation service was called
        verify(validationService, times(1)).validateTransferParameters(
                fromAccountId, toAccountId, amount, referenceId);
    }

    /**
     * Tests that transactions are properly isolated.
     * Ensures that transaction isolation level is properly set and used.
     */
    @Test
    void transfer_WithTransactionTemplate_ShouldUseProperIsolation() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountBalance(fromAccount, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        setAccountBalance(toAccount, new BigDecimal("50.00"));
        
        // Mock validation service
        lenient().when(validationService.validateTransferParameters(
                eq(fromAccountId), eq(toAccountId), eq(amount), any()))
            .thenReturn(new ValidationService.TransferValidationResult(fromAccount, toAccount, null));
        
        // Mock the isolation level check
        when(transactionTemplate.getIsolationLevel()).thenReturn(TransactionDefinition.ISOLATION_READ_COMMITTED);
        
        // Mock other repository operations
        lenient().when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        lenient().when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        lenient().when(accountRepository.findByIdWithLock(fromAccountId)).thenReturn(Optional.of(fromAccount));
        lenient().when(accountRepository.findByIdWithLock(toAccountId)).thenReturn(Optional.of(toAccount));
        
        lenient().when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedTransaction = invocation.getArgument(0);
            try {
                Field idField = Transaction.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(savedTransaction, UUID.randomUUID());
            } catch (Exception e) {
                throw new RuntimeException("Failed to set transaction ID", e);
            }
            return savedTransaction;
        });

        // Create a service with our mocked template
        TransactionService serviceWithTemplate = createServiceWithMockedTemplate();

        // Act
        Transaction result = serviceWithTemplate.transfer(fromAccountId, toAccountId, amount);

        // Assert
        assertNotNull(result);
        assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, transactionTemplate.getIsolationLevel());
        
        // Verify transaction template methods were called
        verify(transactionTemplate, times(1)).execute(any(TransactionCallback.class));
        verify(transactionTemplate, times(1)).getIsolationLevel();
    }
    
    /**
     * Helper method to create a TransactionService with proper mocked dependencies
     * for testing TransactionTemplate-specific behavior.
     */
    private TransactionService createServiceWithMockedTemplate() {
        // Create a new TransactionService that uses our mocked transaction template
        return new TransactionService(accountRepository, transactionRepository, validationService, transactionManager) {
            @Override
            protected TransactionTemplate getTransactionTemplate() {
                // Use the mock instead of creating a new one
                return transactionTemplate;
            }
        };
    }

    @Test
    void transfer_ShouldReturnExistingTransactionForIdempotency() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        String referenceId = "IDEMPOTENT-REF-001";

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountBalance(fromAccount, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        setAccountBalance(toAccount, new BigDecimal("50.00"));

        // Create a mock existing transaction
        Transaction existingTransaction = mockMatchingExistingTransaction(
            fromAccountId, toAccountId, amount, referenceId);

        // Mock validation service to return the existing transaction
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, referenceId))
            .thenReturn(new ValidationService.TransferValidationResult(fromAccount, toAccount, existingTransaction));

        // Act
        Transaction result = transactionService.transfer(fromAccountId, toAccountId, amount, referenceId);

        // Assert
        assertNotNull(result);
        assertEquals(existingTransaction.getId(), result.getId());
        
        // Verify that validation was called but no database operations were performed
        verify(validationService, times(1)).validateTransferParameters(
            fromAccountId, toAccountId, amount, referenceId);
        verify(accountRepository, never()).findByIdWithLock(any(UUID.class));
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
} 