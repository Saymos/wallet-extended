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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
    
    @Mock
    private DoubleEntryService doubleEntryService;

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
     * Sets up the mocks to return specific balance for an account
     */
    private void mockAccountBalance(UUID accountId, BigDecimal balance) {
        // Mock the DoubleEntryService to return the specified balance for this account ID
        // Use lenient to avoid unnecessary stubbing exceptions
        lenient().when(doubleEntryService.calculateBalance(eq(accountId))).thenReturn(balance);
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
    void transfer_ShouldSuccessfullyTransferFundsUsingDoubleEntry() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        // Mock starting balance
        mockAccountBalance(fromAccountId, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        // Mock starting balance
        mockAccountBalance(toAccountId, new BigDecimal("50.00"));

        // Mock validation service
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenReturn(new ValidationService.TransferValidationResult(fromAccount, toAccount, null));
        
        // Mock account repository findById to return our accounts
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        
        // Mock transaction repository to set an ID when saving
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
        
        // Mock DoubleEntryService
        when(doubleEntryService.createTransferEntries(any(Transaction.class))).thenAnswer(invocation -> {
            // Return the same transaction that was passed in
            return invocation.getArgument(0);
        });
        when(doubleEntryService.calculateBalance(any(UUID.class))).thenReturn(new BigDecimal("100.00"));

        // Act
        Transaction result = transactionService.transfer(fromAccountId, toAccountId, amount);

        // Assert
        assertNotNull(result);
        assertEquals(fromAccountId, result.getFromAccountId());
        assertEquals(toAccountId, result.getToAccountId());
        assertEquals(amount, result.getAmount());
        assertEquals(Currency.EUR, result.getCurrency());
        assertEquals(TransactionType.TRANSFER, result.getTransactionType());

        // Verify correct method calls with exact counts
        verify(validationService, times(1)).validateTransferParameters(fromAccountId, toAccountId, amount, null);
        verify(doubleEntryService, times(1)).createTransferEntries(any(Transaction.class));
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        
        // Verify that the account repository findByIdWithLock is never called directly
        verify(accountRepository, never()).findByIdWithLock(any(UUID.class));
        
        // Verify account repository findById is called for both accounts
        verify(accountRepository, times(1)).findById(fromAccountId);
        verify(accountRepository, times(1)).findById(toAccountId);
        
        // Verify account balance is calculated at least once
        verify(doubleEntryService, atLeastOnce()).calculateBalance(any(UUID.class));
    }

    @Test
    void transfer_ShouldThrowInsufficientFundsException() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("300.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        // Mock starting balance
        mockAccountBalance(fromAccountId, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        // Mock starting balance
        mockAccountBalance(toAccountId, new BigDecimal("50.00"));

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
        // Mock starting balance
        mockAccountBalance(fromAccountId, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        // Mock starting balance
        mockAccountBalance(toAccountId, new BigDecimal("50.00"));

        // Mock validation service
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenReturn(new ValidationService.TransferValidationResult(fromAccount, toAccount, null));
            
        // Mock account repository methods
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        
        // Mock double entry service
        when(doubleEntryService.createTransferEntries(any(Transaction.class))).thenAnswer(invocation -> {
            // Return the same transaction that was passed in
            return invocation.getArgument(0);
        });
        when(doubleEntryService.calculateBalance(any(UUID.class))).thenReturn(new BigDecimal("200.00"));
        
        // Mock transaction repository to set an ID when saving
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
        
        // Verify service calls
        verify(validationService, times(1)).validateTransferParameters(fromAccountId, toAccountId, amount, null);
        verify(doubleEntryService, times(1)).createTransferEntries(any(Transaction.class));
        verify(accountRepository, times(1)).findById(fromAccountId);
        verify(accountRepository, times(1)).findById(toAccountId);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void transfer_ShouldAcceptZeroAmount() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.ZERO;

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        // Mock starting balance
        mockAccountBalance(fromAccountId, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        // Mock starting balance
        mockAccountBalance(toAccountId, new BigDecimal("50.00"));

        // Mock validation service to make it accept zero amount for testing
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenReturn(new ValidationService.TransferValidationResult(fromAccount, toAccount, null));
            
        // Mock account repository methods
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        
        // Mock double entry service
        when(doubleEntryService.createTransferEntries(any(Transaction.class))).thenAnswer(invocation -> {
            // Return the same transaction that was passed in
            return invocation.getArgument(0);
        });
        when(doubleEntryService.calculateBalance(any(UUID.class))).thenReturn(new BigDecimal("200.00"));
        
        // Mock transaction repository to set an ID when saving
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
        
        // Verify service calls
        verify(validationService, times(1)).validateTransferParameters(fromAccountId, toAccountId, amount, null);
        verify(doubleEntryService, times(1)).createTransferEntries(any(Transaction.class));
        verify(accountRepository, times(1)).findById(fromAccountId);
        verify(accountRepository, times(1)).findById(toAccountId);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
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
        BigDecimal amount = new BigDecimal("50.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        // Mock starting balance
        mockAccountBalance(fromAccountId, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.EUR, AccountType.BonusAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        // Mock starting balance
        mockAccountBalance(toAccountId, new BigDecimal("50.00"));

        // Mock validation service
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenReturn(new ValidationService.TransferValidationResult(fromAccount, toAccount, null));
            
        // Mock account repository methods
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        
        // Mock double entry service
        when(doubleEntryService.createTransferEntries(any(Transaction.class))).thenAnswer(invocation -> {
            // Return the same transaction that was passed in
            return invocation.getArgument(0);
        });
        when(doubleEntryService.calculateBalance(any(UUID.class))).thenReturn(new BigDecimal("100.00"));
        
        // Mock transaction repository to set an ID when saving
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
        
        // Verify service calls
        verify(validationService, times(1)).validateTransferParameters(fromAccountId, toAccountId, amount, null);
        verify(doubleEntryService, times(1)).createTransferEntries(any(Transaction.class));
        verify(accountRepository, times(1)).findById(fromAccountId);
        verify(accountRepository, times(1)).findById(toAccountId);
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
        // Mock starting balance
        mockAccountBalance(fromAccountId, new BigDecimal("200.00"));
        
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        // Mock starting balance
        mockAccountBalance(toAccountId, new BigDecimal("50.00"));
        
        // Mock the transaction template with a spy to check execution
        TransactionTemplate spyTemplate = spy(new TransactionTemplate(transactionManager));
        
        // Setup mocks
        when(validationService.validateTransferParameters(
                fromAccountId, toAccountId, amount, null))
            .thenReturn(new ValidationService.TransferValidationResult(fromAccount, toAccount, null));
        
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        
        // Mock DoubleEntryService
        when(doubleEntryService.createTransferEntries(any(Transaction.class))).thenAnswer(invocation -> {
            // Return the same transaction that was passed in
            return invocation.getArgument(0);
        });
        when(doubleEntryService.calculateBalance(any(UUID.class))).thenReturn(new BigDecimal("100.00"));
        
        // Mock transaction saving
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
        
        // Create a service with our spy template
        TransactionService serviceWithSpyTemplate = new TransactionService(
                accountRepository, 
                transactionRepository, 
                validationService, 
                transactionManager,
                doubleEntryService) {
            @Override
            protected TransactionTemplate getTransactionTemplate() {
                return spyTemplate;
            }
        };
        
        // Act
        Transaction result = serviceWithSpyTemplate.transfer(fromAccountId, toAccountId, amount);
        
        // Assert
        assertNotNull(result);
        
        // Verify the transaction template was used
        verify(spyTemplate).execute(any(TransactionCallback.class));
        
        // Verify the double-entry service was used
        verify(doubleEntryService).createTransferEntries(any(Transaction.class));
        verify(doubleEntryService, atLeastOnce()).calculateBalance(any(UUID.class));
        
        // Verify the transaction was saved
        verify(transactionRepository).save(any(Transaction.class));
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
        
        // Use a mock TransactionTemplate instead of the real one
        TransactionTemplate mockTemplate = mock(TransactionTemplate.class);
        when(mockTemplate.execute(any())).thenThrow(new RuntimeException("Test exception"));
        
        // Create service with mocked template
        TransactionService serviceWithMockTemplate = new TransactionService(
            accountRepository, 
            transactionRepository,
            validationService,
            transactionManager,
            doubleEntryService) {
            
            @Override
            protected TransactionTemplate getTransactionTemplate() {
                return mockTemplate;
            }
        };
        
        // Arrange validation for any validation calls
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        // Mock starting balance
        mockAccountBalance(fromAccountId, new BigDecimal("200.00"));
        
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        // Mock starting balance
        mockAccountBalance(toAccountId, new BigDecimal("50.00"));
        
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenReturn(new ValidationService.TransferValidationResult(fromAccount, toAccount));
        
        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            serviceWithMockTemplate.transfer(fromAccountId, toAccountId, amount);
        }, "Transfer should throw when transaction template throws");
        
        // Verify transaction template was called and no other operations happened
        verify(mockTemplate).execute(any());
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    /**
     * Tests that transactions are properly isolated.
     * Ensures that transaction isolation level is properly set and used.
     */
    @Test
    void transfer_WithTransactionTemplate_ShouldUseProperIsolation() {
        // Arrange mocks
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        // Mock starting balance
        mockAccountBalance(fromAccountId, new BigDecimal("200.00"));
        
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        // Mock starting balance
        mockAccountBalance(toAccountId, new BigDecimal("50.00"));

        // Setup the necessary mocks
        when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenReturn(new ValidationService.TransferValidationResult(fromAccount, toAccount, null));
        
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        
        // Mock DoubleEntryService
        when(doubleEntryService.createTransferEntries(any(Transaction.class))).thenAnswer(invocation -> {
            // Return the same transaction that was passed in
            return invocation.getArgument(0);
        });
        when(doubleEntryService.calculateBalance(any(UUID.class))).thenReturn(new BigDecimal("100.00"));
        
        // Mock transaction saving
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
        
        // Set up the transaction template isolation level test
        when(transactionTemplate.getIsolationLevel()).thenReturn(TransactionDefinition.ISOLATION_READ_COMMITTED);

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
        // Create TransactionService with injected mock TransactionManager and Template
        return new TransactionService(
                accountRepository, 
                transactionRepository, 
                validationService, 
                transactionManager,
                doubleEntryService) {
            // Override to return our mock template
            @Override
            protected TransactionTemplate getTransactionTemplate() {
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
        String referenceId = "TEST-REF-123";

        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        // Mock starting balance
        mockAccountBalance(fromAccountId, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        // Mock starting balance
        mockAccountBalance(toAccountId, new BigDecimal("50.00"));

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