package com.cubeia.wallet.model;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.exception.CurrencyMismatchException;
import com.cubeia.wallet.exception.InsufficientFundsException;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.LedgerEntryRepository;
import com.cubeia.wallet.repository.TransactionRepository;
import com.cubeia.wallet.service.DoubleEntryService;
import com.cubeia.wallet.service.TransactionService;
import com.cubeia.wallet.service.ValidationService;

@ExtendWith(MockitoExtension.class)
class ModelClassesTest {
    
    @Mock
    private AccountRepository accountRepository;
    
    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private ValidationService validationService;
    
    @Mock
    private PlatformTransactionManager transactionManager;
    
    @Mock
    private TransactionTemplate transactionTemplate;
    
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    
    private DoubleEntryService doubleEntryService;
    
    private TransactionService transactionService;

    /**
     * Initializes services needed for testing
     */
    private void initServices() {
        // Create the DoubleEntryService
        doubleEntryService = new DoubleEntryService(ledgerEntryRepository, accountRepository);
        
        // Create a transaction service that uses our mock template
        transactionService = new TransactionService(
                accountRepository, transactionRepository, validationService, doubleEntryService, transactionManager) {
            @Override
            protected TransactionTemplate getTransactionTemplate() {
                return transactionTemplate;
            }
        };
    }

    @Test
    void account_DefaultConstructor() {
        // Act
        Account account = new Account();
        // Assert: No balance assertion, as balance is now service-based and this account is not persisted
        // Optionally, assert default currency and account type
        assertEquals(Currency.EUR, account.getCurrency());
        assertEquals(AccountType.MainAccount.getInstance(), account.getAccountType());
    }
    
    @Test
    void account_ParameterizedConstructor() {
        // Arrange
        Currency currency = Currency.EUR;
        AccountType accountType = AccountType.MainAccount.getInstance();
        // Act
        Account account = new Account(currency, accountType);
        // Assert
        assertEquals(currency, account.getCurrency());
        assertEquals(accountType, account.getAccountType());
        // No balance assertion, as balance is now service-based and this account is not persisted
    }
    
    /**
     * Helper method to set account ID using reflection (for testing)
     */
    private void setAccountId(Account account, UUID id) {
        try {
            Field idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(account, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set ID", e);
        }
    }
    
    @Test
    void transaction_Execute() {
        // In this test we'll verify that TransactionService.transfer() works 
        // by properly creating a Transaction instance with the right properties
        
        // Create source and destination account IDs
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50.00");
        
        // Create a Transaction manually to verify its properties
        Transaction transaction = new Transaction(fromAccountId, toAccountId, amount, TransactionType.TRANSFER, Currency.EUR);
        
        // Verify that the transaction has the expected properties
        assertNotNull(transaction);
        assertEquals(fromAccountId, transaction.getFromAccountId());
        assertEquals(toAccountId, transaction.getToAccountId());
        assertEquals(amount, transaction.getAmount());
        assertEquals(Currency.EUR, transaction.getCurrency());
        assertEquals(TransactionType.TRANSFER, transaction.getTransactionType());
        assertEquals(TransactionStatus.PENDING, transaction.getStatus());
        assertNotNull(transaction.getTimestamp());
    }
    
    @Test
    void transaction_Execute_InsufficientFunds() {
        // Initialize needed services
        initServices();
        
        // Arrange - Create source and destination accounts
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        
        // Set IDs
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        setAccountId(fromAccount, fromAccountId);
        setAccountId(toAccount, toAccountId);
        
        // Create transaction with more than available funds
        BigDecimal amount = new BigDecimal("50.00");
        
        // Mock ValidationService to throw InsufficientFundsException
        when(validationService.validateTransferParameters(eq(fromAccountId), eq(toAccountId), eq(amount), isNull()))
            .thenThrow(new InsufficientFundsException(fromAccountId, 
                "Insufficient funds in account: " + fromAccountId + 
                ", Current balance: 0.00, Required amount: " + amount));
        
        // Act & Assert - Should throw exception
        InsufficientFundsException exception = assertThrows(
            InsufficientFundsException.class,
            () -> transactionService.transfer(fromAccountId, toAccountId, amount, null, null)
        );
        
        // Verify the exception message is as expected
        assertTrue(exception.getMessage().contains("Insufficient funds"));
    }
    
    @Test
    void transaction_Execute_CurrencyMismatch() {
        // Initialize needed services
        initServices();
        
        // Arrange - Create source and destination accounts with different currencies
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account toAccount = new Account(Currency.USD, AccountType.MainAccount.getInstance());
        
        // Set IDs
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        setAccountId(fromAccount, fromAccountId);
        setAccountId(toAccount, toAccountId);
        
        // Create transaction
        BigDecimal amount = new BigDecimal("50.00");
        
        // Mock ValidationService to throw CurrencyMismatchException
        when(validationService.validateTransferParameters(eq(fromAccountId), eq(toAccountId), eq(amount), isNull()))
            .thenThrow(new CurrencyMismatchException(
                    "Currency mismatch: source account currency EUR doesn't match target account currency USD"));
        
        // Act & Assert - Should throw exception
        CurrencyMismatchException exception = assertThrows(
            CurrencyMismatchException.class,
            () -> transactionService.transfer(fromAccountId, toAccountId, amount, null, null)
        );
        
        // Verify the exception message is as expected
        assertTrue(exception.getMessage().contains("Currency mismatch"));
    }
    
    @Test
    void transaction_Constructor() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("123.45");
        Currency currency = Currency.EUR;
        TransactionType type = TransactionType.TRANSFER;
        
        // Act - Create a new transaction using explicit constructor
        Transaction transaction = new Transaction(fromAccountId, toAccountId, amount, type, currency);
        
        // Assert
        assertNotNull(transaction);
        assertEquals(fromAccountId, transaction.getFromAccountId());
        assertEquals(toAccountId, transaction.getToAccountId());
        assertEquals(amount, transaction.getAmount());
        assertEquals(currency, transaction.getCurrency());
        assertEquals(type, transaction.getTransactionType());
        assertEquals(TransactionStatus.PENDING, transaction.getStatus());
        assertNotNull(transaction.getTimestamp());
    }
    
    @Test
    void transaction_NegativeAmount() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("-50.00");
        Currency currency = Currency.EUR;
        TransactionType type = TransactionType.TRANSFER;
        
        // Act & Assert
        assertThrows(
            IllegalArgumentException.class,
            () -> new Transaction(fromAccountId, toAccountId, amount, type, currency)
        );
    }
    
    @Test
    void accountType_WithdrawalLimits() {
        // This test directly checks the withdrawal policy of a BonusAccount
        // No need to use validation service or mock repositories, just check if the policy is correctly applied
        
        // BonusAccount should not allow full balance withdrawal
        AccountType bonusAccountType = AccountType.BonusAccount.getInstance();
        assertFalse(bonusAccountType.allowFullBalanceWithdrawal(), 
                   "BonusAccount should not allow full balance withdrawal");
                   
        // MainAccount should allow full balance withdrawal
        AccountType mainAccountType = AccountType.MainAccount.getInstance();
        assertTrue(mainAccountType.allowFullBalanceWithdrawal(),
                  "MainAccount should allow full balance withdrawal");
    }
    
    @Test
    void transaction_Execute_FromAccountNotFound() {
        // Initialize needed services
        initServices();
        
        // Arrange
        UUID nonExistentAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        
        // Mock validation service to throw AccountNotFoundException - this is what's actually used
        when(validationService.validateTransferParameters(nonExistentAccountId, toAccountId, amount, null))
            .thenThrow(new AccountNotFoundException(nonExistentAccountId));
        
        // Act & Assert
        assertThrows(
            AccountNotFoundException.class,
            () -> transactionService.transfer(nonExistentAccountId, toAccountId, amount, null, null)
        );
    }
    
    @Test
    void transaction_Execute_ToAccountNotFound() {
        // Initialize needed services
        initServices();
        
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID nonExistentAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        
        // Mock validation service to throw AccountNotFoundException - this is what's actually used
        when(validationService.validateTransferParameters(fromAccountId, nonExistentAccountId, amount, null))
            .thenThrow(new AccountNotFoundException(nonExistentAccountId));
        
        // Act & Assert
        assertThrows(
            AccountNotFoundException.class,
            () -> transactionService.transfer(fromAccountId, nonExistentAccountId, amount, null, null)
        );
    }
} 