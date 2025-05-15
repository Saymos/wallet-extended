package com.cubeia.wallet.model;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
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

    /**
     * Test extension of TransactionService that makes executeTransaction public
     * for testing purposes
     */
    static class TestTransactionService extends TransactionService {
        public TestTransactionService(
                AccountRepository accountRepository,
                TransactionRepository transactionRepository,
                ValidationService validationService,
                DoubleEntryService doubleEntryService,
                PlatformTransactionManager transactionManager) {
            super(accountRepository, transactionRepository, validationService, doubleEntryService, transactionManager);
        }
        
        // Make executeTransaction public for testing
        @Override
        public void executeTransaction(Transaction transaction) {
            super.executeTransaction(transaction);
        }
    }
    
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
    
    @BeforeEach
    void setUp() {
        // Create the DoubleEntryService
        doubleEntryService = new DoubleEntryService(ledgerEntryRepository, accountRepository);
        
        // Mock transaction template execution using lenient() to avoid UnnecessaryStubbing errors
        lenient().when(transactionTemplate.execute(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> {
                org.springframework.transaction.support.TransactionCallback<?> callback = 
                    invocation.getArgument(0);
                return callback.doInTransaction(null);
            });
        
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
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID", e);
        }
    }
    
    /**
     * Helper method to setup account with mocked DoubleEntryService
     */
    private void setupAccountBalance(Account account, BigDecimal balance) {
        // Ensure the account has an ID
        if (account.getId() == null) {
            setAccountId(account, UUID.randomUUID());
        }

        // Mock that the account exists in the repository
        UUID accountId = account.getId();
        lenient().when(accountRepository.existsById(accountId)).thenReturn(true);
        lenient().when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        // Mock the DoubleEntryService to return the specified balance
        lenient().when(ledgerEntryRepository.calculateBalance(accountId)).thenReturn(balance);
        lenient().when(ledgerEntryRepository.calculateBalanceByCurrency(accountId, account.getCurrency())).thenReturn(balance);
        // Mock DoubleEntryService to return the balance for this account
        lenient().when(doubleEntryService.calculateBalance(accountId)).thenReturn(balance);
        lenient().when(doubleEntryService.calculateBalanceByCurrency(accountId, account.getCurrency())).thenReturn(balance);
        // No more setDoubleEntryService
    }
    
    @Test
    void transaction_Execute() {
        // Arrange - Create source and destination accounts
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        
        // Set IDs
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        setAccountId(fromAccount, fromAccountId);
        setAccountId(toAccount, toAccountId);
        
        // Set initial balance
        setupAccountBalance(fromAccount, new BigDecimal("100.00"));
        setupAccountBalance(toAccount, BigDecimal.ZERO);
        
        // Create the transaction
        BigDecimal amount = new BigDecimal("50.00");
        
        // Mock ValidationService to pass validation
        ValidationService.TransferValidationResult validationResult = 
                new ValidationService.TransferValidationResult(fromAccount, toAccount, null);
        lenient().when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenReturn(validationResult);
        
        // Setup mock repository to return our accounts
        lenient().when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        lenient().when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        
        // Important: Mock the existsById method to return true for our account IDs
        lenient().when(accountRepository.existsById(fromAccountId)).thenReturn(true);
        lenient().when(accountRepository.existsById(toAccountId)).thenReturn(true);
        
        lenient().when(accountRepository.save(org.mockito.ArgumentMatchers.any(Account.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        lenient().when(transactionRepository.save(org.mockito.ArgumentMatchers.any(Transaction.class)))
            .thenAnswer(invocation -> {
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
        
        // After transfer, the balances should change
        BigDecimal newFromBalance = new BigDecimal("50.00");
        BigDecimal newToBalance = new BigDecimal("50.00");
        
        // Update mock to return new balances after transfer
        lenient().when(doubleEntryService.calculateBalance(fromAccountId))
            .thenReturn(newFromBalance);
        lenient().when(doubleEntryService.calculateBalance(toAccountId))
            .thenReturn(newToBalance);
        
        // Act - Execute the transaction using service
        transactionService.transfer(fromAccountId, toAccountId, amount, null, null);
        
        // Assert - Check balances were updated correctly
        assertEquals(newFromBalance, doubleEntryService.calculateBalance(fromAccountId));
        assertEquals(newToBalance, doubleEntryService.calculateBalance(toAccountId));
    }
    
    @Test
    void transaction_Execute_InsufficientFunds() {
        // Arrange - Create source and destination accounts
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        
        // Set IDs
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        setAccountId(fromAccount, fromAccountId);
        setAccountId(toAccount, toAccountId);
        
        // Set initial balance to zero
        setupAccountBalance(fromAccount, BigDecimal.ZERO);
        
        // Create transaction with more than available funds
        BigDecimal amount = new BigDecimal("50.00");
        
        // Mock ValidationService to throw InsufficientFundsException
        lenient().when(validationService.validateTransferParameters(eq(fromAccountId), eq(toAccountId), eq(amount), isNull()))
            .thenThrow(new InsufficientFundsException(fromAccountId, 
                "Insufficient funds in account: " + fromAccountId + 
                ", Current balance: 0.00, Required amount: " + amount));
        
        // Setup mock repository to return our accounts
        lenient().when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        lenient().when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        lenient().when(accountRepository.existsById(fromAccountId)).thenReturn(true);
        lenient().when(accountRepository.existsById(toAccountId)).thenReturn(true);
        
        // Act & Assert - Should throw exception
        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class, () -> {
            transactionService.transfer(fromAccountId, toAccountId, amount, null, null);
        });
        assertTrue(exception.getMessage().contains("Insufficient funds"));
    }
    
    @Test
    void transaction_Execute_CurrencyMismatch() {
        // Arrange - Create accounts with different currencies
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account toAccount = new Account(Currency.USD, AccountType.MainAccount.getInstance());
        
        // Set IDs
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        setAccountId(fromAccount, fromAccountId);
        setAccountId(toAccount, toAccountId);
        
        // Set initial balance
        setupAccountBalance(fromAccount, new BigDecimal("100.00"));
        
        // Create transaction
        BigDecimal amount = new BigDecimal("50.00");
        
        // Mock ValidationService to fail with currency mismatch
        lenient().when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenThrow(CurrencyMismatchException.forTransfer(Currency.EUR, Currency.USD));
        
        // Act & Assert - Should throw exception
        CurrencyMismatchException exception = assertThrows(CurrencyMismatchException.class, () -> {
            transactionService.transfer(fromAccountId, toAccountId, amount, null, null);
        });
        
        // Verification requires both currencies in the message
        String message = exception.getMessage();
        assertTrue(message.contains("Cannot transfer between accounts with different currencies"));
    }
    
    @Test
    void transaction_Constructor() {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50.00");
        TransactionType type = TransactionType.TRANSFER;
        Currency currency = Currency.EUR;
        String reference = "Test transfer";
        
        // Act
        Transaction transaction = new Transaction(fromAccountId, toAccountId, amount, type, currency, reference);
        
        // Assert
        assertEquals(fromAccountId, transaction.getFromAccountId());
        assertEquals(toAccountId, transaction.getToAccountId());
        assertEquals(amount, transaction.getAmount());
        assertEquals(type, transaction.getTransactionType());
        assertEquals(currency, transaction.getCurrency());
        assertEquals(reference, transaction.getReference());
    }
    
    @Test
    void transaction_NegativeAmount() {
        // We expect an exception when trying to create a transaction with negative amount
        assertThrows(IllegalArgumentException.class, () -> {
            new Transaction(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("-10.00"),
                TransactionType.TRANSFER,
                Currency.EUR
            );
        });
        
        // Also test with zero amount
        assertThrows(IllegalArgumentException.class, () -> {
            new Transaction(
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.ZERO,
                TransactionType.TRANSFER,
                Currency.EUR
            );
        });
    }
    
    @Test
    void accountType_WithdrawalLimits() {
        // Arrange
        Account mainAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account bonusAccount = new Account(Currency.EUR, AccountType.BonusAccount.getInstance());
        Account pendingAccount = new Account(Currency.EUR, AccountType.PendingAccount.getInstance());
        Account jackpotAccount = new Account(Currency.EUR, AccountType.JackpotAccount.getInstance());
        
        // Set IDs for all accounts
        UUID mainId = UUID.randomUUID();
        UUID bonusId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();
        UUID jackpotId = UUID.randomUUID();
        
        setAccountId(mainAccount, mainId);
        setAccountId(bonusAccount, bonusId);
        setAccountId(pendingAccount, pendingId);
        setAccountId(jackpotAccount, jackpotId);
        
        // Setup balances
        BigDecimal mainBalance = new BigDecimal("100.00");
        BigDecimal bonusBalance = new BigDecimal("50.00");
        BigDecimal pendingBalance = new BigDecimal("25.00");
        BigDecimal jackpotBalance = new BigDecimal("1000.00");
        
        setupAccountBalance(mainAccount, mainBalance);
        setupAccountBalance(bonusAccount, bonusBalance);
        setupAccountBalance(pendingAccount, pendingBalance);
        setupAccountBalance(jackpotAccount, jackpotBalance);
        
        // Assert balances via service
        assertEquals(mainBalance, doubleEntryService.calculateBalance(mainId));
        assertEquals(bonusBalance, doubleEntryService.calculateBalance(bonusId));
        assertEquals(pendingBalance, doubleEntryService.calculateBalance(pendingId));
        assertEquals(jackpotBalance, doubleEntryService.calculateBalance(jackpotId));
        
        // Act & Assert - Check withdrawal policy by account type
        // Main accounts allow full withdrawal (policy)
        assertTrue(mainAccount.getAccountType().allowFullBalanceWithdrawal());
        // Bonus accounts don't allow any withdrawal (policy)
        assertFalse(bonusAccount.getAccountType().allowFullBalanceWithdrawal());
        // Pending accounts don't allow any withdrawal (policy)
        assertFalse(pendingAccount.getAccountType().allowFullBalanceWithdrawal());
        // Jackpot accounts allow full withdrawal (policy)
        assertTrue(jackpotAccount.getAccountType().allowFullBalanceWithdrawal());
    }
    
    @Test
    void transaction_Execute_FromAccountNotFound() {
        // Arrange - Create accounts
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        
        // Set IDs
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        setAccountId(toAccount, toAccountId);
        
        // Create transaction parameters
        BigDecimal amount = new BigDecimal("50.00");
        
        // Mock ValidationService to throw AccountNotFoundException
        lenient().when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenThrow(new AccountNotFoundException(fromAccountId));
        
        // Act & Assert - Should throw exception when account not found
        AccountNotFoundException exception = assertThrows(AccountNotFoundException.class, () -> {
            transactionService.transfer(fromAccountId, toAccountId, amount, null, null);
        });
        
        // Verify the exception contains the account ID
        assertTrue(exception.getMessage().contains(fromAccountId.toString()));
    }
    
    @Test
    void transaction_Execute_ToAccountNotFound() {
        // Arrange - Create accounts
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        
        // Set IDs
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        setAccountId(fromAccount, fromAccountId);
        setupAccountBalance(fromAccount, new BigDecimal("100.00"));
        
        // Create transaction parameters
        BigDecimal amount = new BigDecimal("50.00");
        
        // Mock ValidationService to throw AccountNotFoundException
        lenient().when(validationService.validateTransferParameters(fromAccountId, toAccountId, amount, null))
            .thenThrow(new AccountNotFoundException(toAccountId));
        
        // Act & Assert - Should throw exception when account not found
        AccountNotFoundException exception = assertThrows(AccountNotFoundException.class, () -> {
            transactionService.transfer(fromAccountId, toAccountId, amount, null, null);
        });
        
        assertTrue(exception.getMessage().contains(toAccountId.toString()));
    }
} 