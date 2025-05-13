package com.cubeia.wallet.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class ModelClassesTest {

    @Test
    void account_DefaultConstructorAndGettersSetters() {
        // Act - Create with default constructor
        Account account = new Account();
        
        // Assert - Initial state
        assertNull(account.getId());
        assertNull(account.getBalance());
        
        // Arrange - Values to set
        Long id = 1L;
        BigDecimal balance = new BigDecimal("100.00");
        
        // Act - Set values
        account.setId(id);
        account.setBalance(balance);
        
        // Assert - After setting values
        assertEquals(id, account.getId());
        assertEquals(balance, account.getBalance());
    }
    
    @Test
    void transaction_DefaultConstructorAndGettersSetters() {
        // Act - Create with default constructor
        Transaction transaction = new Transaction();
        
        // Assert - Initial state
        assertNull(transaction.getId());
        assertNull(transaction.getFromAccountId());
        assertNull(transaction.getToAccountId());
        assertNull(transaction.getAmount());
        assertNull(transaction.getTimestamp());
        
        // Arrange - Values to set
        Long id = 1L;
        Long fromAccountId = 2L;
        Long toAccountId = 3L;
        BigDecimal amount = new BigDecimal("50.00");
        LocalDateTime timestamp = LocalDateTime.now();
        
        // Act - Set values
        transaction.setId(id);
        transaction.setFromAccountId(fromAccountId);
        transaction.setToAccountId(toAccountId);
        transaction.setAmount(amount);
        transaction.setTimestamp(timestamp);
        
        // Assert - After setting values
        assertEquals(id, transaction.getId());
        assertEquals(fromAccountId, transaction.getFromAccountId());
        assertEquals(toAccountId, transaction.getToAccountId());
        assertEquals(amount, transaction.getAmount());
        assertEquals(timestamp, transaction.getTimestamp());
    }
} 