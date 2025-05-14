package com.cubeia.wallet.model;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ModelClassesTest {

    @Test
    void account_DefaultConstructor() {
        // Act - Create with default constructor
        Account account = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        
        // Assert - Initial state
        assertNotNull(account.getBalance());
        assertEquals(BigDecimal.ZERO, account.getBalance());
        assertEquals(Currency.EUR, account.getCurrency());
        assertEquals(AccountType.MainAccount.getInstance(), account.getAccountType());
    }
    
    @Test
    void account_ParameterizedConstructor() {
        // Act - Create with parameterized constructor
        Account account = new Account(Currency.USD, AccountType.BonusAccount.getInstance());
        
        // Assert - Initial state
        assertNotNull(account.getBalance());
        assertEquals(BigDecimal.ZERO, account.getBalance());
        assertEquals(Currency.USD, account.getCurrency());
        assertEquals(AccountType.BonusAccount.getInstance(), account.getAccountType());
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
    
    @Test
    void transaction_Execute() {
        // Arrange - Create source and destination accounts
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        
        // Set IDs so we can match with the transaction
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        setAccountId(fromAccount, fromAccountId);
        setAccountId(toAccount, toAccountId);
        
        // Set initial balance in source account
        setAccountBalance(fromAccount, new BigDecimal("100.00"));
        
        // Create the transaction
        BigDecimal amount = new BigDecimal("50.00");
        Transaction transaction = new Transaction(
            fromAccount.getId(), 
            toAccount.getId(), 
            amount,
            TransactionType.TRANSFER,
            Currency.EUR
        );
        
        // Act - Execute the transaction
        transaction.execute(transaction, fromAccount, toAccount);
        
        // Assert - Check balances were updated correctly
        assertEquals(new BigDecimal("50.00"), fromAccount.getBalance());
        assertEquals(new BigDecimal("50.00"), toAccount.getBalance());
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
        
        // Create transaction with more than available funds
        BigDecimal amount = new BigDecimal("50.00");
        Transaction transaction = new Transaction(
            fromAccount.getId(), 
            toAccount.getId(), 
            amount,
            TransactionType.TRANSFER,
            Currency.EUR
        );
        
        // Act & Assert - Should throw exception
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transaction.execute(transaction, fromAccount, toAccount);
        });
        assertTrue(exception.getMessage().contains("Insufficient funds in account"));
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
        setAccountBalance(fromAccount, new BigDecimal("100.00"));
        
        // Create transaction
        Transaction transaction = new Transaction(
            fromAccount.getId(), 
            toAccount.getId(), 
            new BigDecimal("50.00"),
            TransactionType.TRANSFER,
            Currency.EUR
        );
        
        // Act & Assert - Should throw exception
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transaction.execute(transaction, fromAccount, toAccount);
        });
        assertTrue(exception.getMessage().contains("Currency mismatch"));
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
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        BigDecimal negativeAmount = new BigDecimal("-50.00");
        TransactionType type = TransactionType.TRANSFER;
        Currency currency = Currency.EUR;
        
        // Act
        Transaction transaction = new Transaction(fromAccountId, toAccountId, negativeAmount, type, currency);
        
        // Assert
        // The Transaction class is now a simple data container and no longer validates amounts
        // Validation is now done in the TransactionService
        assertEquals(negativeAmount, transaction.getAmount());
    }
    
    @Test
    void accountType_WithdrawalLimits() {
        // Arrange
        Account mainAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account bonusAccount = new Account(Currency.EUR, AccountType.BonusAccount.getInstance());
        Account pendingAccount = new Account(Currency.EUR, AccountType.PendingAccount.getInstance());
        Account jackpotAccount = new Account(Currency.EUR, AccountType.JackpotAccount.getInstance());
        
        // Set balances for all accounts
        setAccountBalance(mainAccount, new BigDecimal("100.00"));
        setAccountBalance(bonusAccount, new BigDecimal("100.00"));
        setAccountBalance(pendingAccount, new BigDecimal("100.00"));
        setAccountBalance(jackpotAccount, new BigDecimal("100.00"));
        
        // Assert - Check withdrawal limits
        assertEquals(new BigDecimal("100.00"), mainAccount.getMaxWithdrawalAmount());
        assertEquals(BigDecimal.ZERO, bonusAccount.getMaxWithdrawalAmount());
        assertEquals(BigDecimal.ZERO, pendingAccount.getMaxWithdrawalAmount());
        assertEquals(new BigDecimal("100.00"), jackpotAccount.getMaxWithdrawalAmount());
    }
    
    @Test
    void transaction_Execute_WrongTransactionInstance() {
        // Arrange - Create source and destination accounts
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        
        // Set IDs
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        setAccountId(fromAccount, fromAccountId);
        setAccountId(toAccount, toAccountId);
        
        // Set initial balance
        setAccountBalance(fromAccount, new BigDecimal("100.00"));
        
        // Create two separate transaction instances with the same data
        Transaction transaction1 = new Transaction(
            fromAccount.getId(), 
            toAccount.getId(), 
            new BigDecimal("50.00"),
            TransactionType.TRANSFER,
            Currency.EUR
        );
        
        Transaction transaction2 = new Transaction(
            fromAccount.getId(), 
            toAccount.getId(), 
            new BigDecimal("50.00"),
            TransactionType.TRANSFER,
            Currency.EUR
        );
        
        // Act & Assert - Should throw exception when using wrong transaction instance
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transaction1.execute(transaction2, fromAccount, toAccount);
        });
        
        assertTrue(exception.getMessage().contains("Transaction parameter must be the same instance as 'this'"));
        
        // Verify balances were not changed
        assertEquals(new BigDecimal("100.00"), fromAccount.getBalance());
        assertEquals(BigDecimal.ZERO, toAccount.getBalance());
    }
    
    @Test
    void transaction_Execute_AccountIdMismatch() {
        // Arrange - Create accounts
        Account correctFromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account correctToAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        Account wrongAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        
        // Set IDs
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        UUID wrongAccountId = UUID.randomUUID();
        
        setAccountId(correctFromAccount, fromAccountId);
        setAccountId(correctToAccount, toAccountId);
        setAccountId(wrongAccount, wrongAccountId);
        
        // Set initial balance
        setAccountBalance(correctFromAccount, new BigDecimal("100.00"));
        
        // Create transaction
        Transaction transaction = new Transaction(
            fromAccountId, 
            toAccountId, 
            new BigDecimal("50.00"),
            TransactionType.TRANSFER,
            Currency.EUR
        );
        
        // Act & Assert - Should throw exception when using wrong fromAccount
        IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class, () -> {
            transaction.execute(transaction, wrongAccount, correctToAccount);
        });
        
        assertTrue(exception1.getMessage().contains("Account IDs do not match transaction record"));
        
        // Verify balances were not changed
        assertEquals(new BigDecimal("100.00"), correctFromAccount.getBalance());
        assertEquals(BigDecimal.ZERO, correctToAccount.getBalance());
        
        // Act & Assert - Should throw exception when using wrong toAccount
        IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class, () -> {
            transaction.execute(transaction, correctFromAccount, wrongAccount);
        });
        
        assertTrue(exception2.getMessage().contains("Account IDs do not match transaction record"));
        
        // Verify balances were not changed
        assertEquals(new BigDecimal("100.00"), correctFromAccount.getBalance());
        assertEquals(BigDecimal.ZERO, correctToAccount.getBalance());
    }
} 