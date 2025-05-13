package com.cubeia.wallet.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TransactionTest {

    private Account fromAccount;
    private Account toAccount;
    private UUID fromAccountId;
    private UUID toAccountId;
    private Currency currency;
    private BigDecimal amount;
    private Transaction transaction;

    @BeforeEach
    public void setup() throws Exception {
        // Setup test data
        fromAccountId = UUID.randomUUID();
        toAccountId = UUID.randomUUID();
        currency = Currency.USD;
        amount = BigDecimal.valueOf(100);
        
        // Create accounts with the available constructor
        fromAccount = new Account(currency, AccountType.MainAccount.getInstance());
        toAccount = new Account(currency, AccountType.MainAccount.getInstance());
        
        // Set ID and balance using reflection since they're normally set by JPA
        setPrivateField(fromAccount, "id", fromAccountId);
        setPrivateField(fromAccount, "balance", BigDecimal.valueOf(1000));
        
        setPrivateField(toAccount, "id", toAccountId);
        setPrivateField(toAccount, "balance", BigDecimal.valueOf(500));
        
        transaction = new Transaction(fromAccountId, toAccountId, amount, 
                TransactionType.TRANSFER, currency);
    }

    /**
     * Helper method to set private fields using reflection
     */
    private void setPrivateField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    @Test
    public void testExecuteWithWrongTransactionInstance() {
        // Create a different transaction instance
        Transaction differentTransaction = new Transaction(fromAccountId, toAccountId, amount, 
                TransactionType.TRANSFER, currency);
        
        // Should throw exception when wrong transaction instance is used
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transaction.execute(differentTransaction, fromAccount, toAccount);
        });
        
        assertEquals("Transaction parameter must be the same instance as 'this'", exception.getMessage());
        
        // Verify balances are unchanged
        assertEquals(BigDecimal.valueOf(1000), fromAccount.getBalance());
        assertEquals(BigDecimal.valueOf(500), toAccount.getBalance());
    }
    
    @Test
    public void testExecuteWithMismatchedFromAccountId() throws Exception {
        // Create account with different ID
        UUID differentId = UUID.randomUUID();
        Account differentFromAccount = new Account(currency, AccountType.MainAccount.getInstance());
        setPrivateField(differentFromAccount, "id", differentId);
        setPrivateField(differentFromAccount, "balance", BigDecimal.valueOf(1000));
        
        // Should throw exception when account ID doesn't match
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transaction.execute(transaction, differentFromAccount, toAccount);
        });
        
        // Verify exception contains expected information
        assertTrue(exception.getMessage().contains("Account IDs do not match"));
        assertTrue(exception.getMessage().contains(fromAccountId.toString()));
        assertTrue(exception.getMessage().contains(differentId.toString()));
        
        // Verify balances are unchanged
        assertEquals(BigDecimal.valueOf(1000), differentFromAccount.getBalance());
        assertEquals(BigDecimal.valueOf(500), toAccount.getBalance());
    }
    
    @Test
    public void testExecuteWithMismatchedToAccountId() throws Exception {
        // Create account with different ID
        UUID differentId = UUID.randomUUID();
        Account differentToAccount = new Account(currency, AccountType.MainAccount.getInstance());
        setPrivateField(differentToAccount, "id", differentId);
        setPrivateField(differentToAccount, "balance", BigDecimal.valueOf(500));
        
        // Should throw exception when account ID doesn't match
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transaction.execute(transaction, fromAccount, differentToAccount);
        });
        
        // Verify exception contains expected information
        assertTrue(exception.getMessage().contains("Account IDs do not match"));
        assertTrue(exception.getMessage().contains(toAccountId.toString()));
        assertTrue(exception.getMessage().contains(differentId.toString()));
        
        // Verify balances are unchanged
        assertEquals(BigDecimal.valueOf(1000), fromAccount.getBalance());
        assertEquals(BigDecimal.valueOf(500), differentToAccount.getBalance());
    }
    
    @Test
    public void testExecuteWithFromAccountCurrencyMismatch() throws Exception {
        // Create account with different currency
        Account differentCurrencyAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setPrivateField(differentCurrencyAccount, "id", fromAccountId);
        setPrivateField(differentCurrencyAccount, "balance", BigDecimal.valueOf(1000));
        
        // Should throw exception when currency doesn't match
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transaction.execute(transaction, differentCurrencyAccount, toAccount);
        });
        
        // Verify exception contains expected information
        assertTrue(exception.getMessage().contains("Currency mismatch"));
        assertTrue(exception.getMessage().contains(currency.toString()));
        assertTrue(exception.getMessage().contains(Currency.EUR.toString()));
    }
    
    @Test
    public void testExecuteWithToAccountCurrencyMismatch() throws Exception {
        // Create account with different currency
        Account differentCurrencyAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setPrivateField(differentCurrencyAccount, "id", toAccountId);
        setPrivateField(differentCurrencyAccount, "balance", BigDecimal.valueOf(500));
        
        // Should throw exception when currency doesn't match
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transaction.execute(transaction, fromAccount, differentCurrencyAccount);
        });
        
        // Verify exception contains expected information
        assertTrue(exception.getMessage().contains("Currency mismatch"));
        assertTrue(exception.getMessage().contains(currency.toString()));
        assertTrue(exception.getMessage().contains(Currency.EUR.toString()));
    }

    @Test
    public void testExecuteMethodWithMismatchedAccountIds() throws Exception {
        // Setup accounts with mismatched IDs
        UUID wrongFromAccountId = UUID.randomUUID();
        Account wrongFromAccount = new Account(currency, AccountType.MainAccount.getInstance());
        setPrivateField(wrongFromAccount, "id", wrongFromAccountId); // not matching fromAccountId
        setPrivateField(wrongFromAccount, "balance", BigDecimal.valueOf(200));
        
        // When execute is called with mismatched account IDs
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transaction.execute(transaction, wrongFromAccount, toAccount);
        });
        
        // Then exception should mention account IDs
        assertTrue(exception.getMessage().contains("Account IDs do not match transaction record"));
        assertTrue(exception.getMessage().contains(fromAccountId.toString()));
        assertTrue(exception.getMessage().contains(wrongFromAccountId.toString()));
    }
    
    @Test
    public void testExecuteMethodWithWrongTransactionInstance() {
        // Setup a different transaction instance
        Transaction differentTransaction = new Transaction(
            fromAccountId, toAccountId, BigDecimal.valueOf(50), TransactionType.TRANSFER, currency);
            
        // When execute is called with wrong transaction instance
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transaction.execute(differentTransaction, fromAccount, toAccount);
        });
        
        // Then exception should mention transaction instance
        assertEquals("Transaction parameter must be the same instance as 'this'", 
            exception.getMessage());
    }
} 