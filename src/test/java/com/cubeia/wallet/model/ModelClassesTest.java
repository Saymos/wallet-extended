package com.cubeia.wallet.model;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class ModelClassesTest {

    @Test
    void account_DefaultConstructor() {
        // Act - Create with default constructor
        Account account = new Account(Currency.EUR, AccountType.MAIN);
        
        // Assert - Initial state
        assertNotNull(account.getBalance());
        assertEquals(BigDecimal.ZERO, account.getBalance());
        assertEquals(Currency.EUR, account.getCurrency());
        assertEquals(AccountType.MAIN, account.getAccountType());
    }
    
    @Test
    void account_ParameterizedConstructor() {
        // Act - Create with parameterized constructor
        Account account = new Account(Currency.USD, AccountType.BONUS);
        
        // Assert - Initial state
        assertNotNull(account.getBalance());
        assertEquals(BigDecimal.ZERO, account.getBalance());
        assertEquals(Currency.USD, account.getCurrency());
        assertEquals(AccountType.BONUS, account.getAccountType());
    }
    
    /**
     * Helper method to set account ID using reflection (for testing)
     */
    private void setAccountId(Account account, Long id) {
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
        Account fromAccount = new Account(Currency.EUR, AccountType.MAIN);
        Account toAccount = new Account(Currency.EUR, AccountType.MAIN);
        
        // Set IDs so we can match with the transaction
        setAccountId(fromAccount, 1L);
        setAccountId(toAccount, 2L);
        
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
        Account fromAccount = new Account(Currency.EUR, AccountType.MAIN);
        Account toAccount = new Account(Currency.EUR, AccountType.MAIN);
        
        // Set IDs
        setAccountId(fromAccount, 1L);
        setAccountId(toAccount, 2L);
        
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
        assertEquals("Insufficient funds in account: 1", exception.getMessage());
    }
    
    @Test
    void transaction_Execute_CurrencyMismatch() {
        // Arrange - Create accounts with different currencies
        Account fromAccount = new Account(Currency.EUR, AccountType.MAIN);
        Account toAccount = new Account(Currency.USD, AccountType.MAIN);
        
        // Set IDs
        setAccountId(fromAccount, 1L);
        setAccountId(toAccount, 2L);
        
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
        assertEquals("Currency mismatch: Transaction and accounts must use the same currency", exception.getMessage());
    }
    
    @Test
    void transaction_Constructor() {
        // Arrange
        Long fromAccountId = 1L;
        Long toAccountId = 2L;
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
        Long fromAccountId = 1L;
        Long toAccountId = 2L;
        BigDecimal negativeAmount = new BigDecimal("-50.00");
        TransactionType type = TransactionType.TRANSFER;
        Currency currency = Currency.EUR;
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new Transaction(fromAccountId, toAccountId, negativeAmount, type, currency);
        });
    }
} 