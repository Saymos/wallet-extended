package com.cubeia.wallet.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AccountTypeConverterTest {

    private final AccountTypeConverter converter = new AccountTypeConverter();
    
    @Test
    void convertToDatabaseColumn_ShouldReturnNullForNullInput() {
        // Act
        String result = converter.convertToDatabaseColumn(null);
        
        // Assert
        assertNull(result);
    }
    
    @Test
    void convertToDatabaseColumn_ShouldReturnAccountTypeName() {
        // Act
        String result1 = converter.convertToDatabaseColumn(AccountType.MainAccount.getInstance());
        String result2 = converter.convertToDatabaseColumn(AccountType.BonusAccount.getInstance());
        String result3 = converter.convertToDatabaseColumn(AccountType.PendingAccount.getInstance());
        String result4 = converter.convertToDatabaseColumn(AccountType.JackpotAccount.getInstance());
        
        // Assert
        assertEquals("MAIN", result1);
        assertEquals("BONUS", result2);
        assertEquals("PENDING", result3);
        assertEquals("JACKPOT", result4);
    }
    
    @Test
    void convertToEntityAttribute_ShouldReturnNullForNullInput() {
        // Act
        AccountType result = converter.convertToEntityAttribute(null);
        
        // Assert
        assertNull(result);
    }
    
    @Test
    void convertToEntityAttribute_ShouldReturnAccountTypeForValidString() {
        // Act
        AccountType result1 = converter.convertToEntityAttribute("MAIN");
        AccountType result2 = converter.convertToEntityAttribute("BONUS");
        AccountType result3 = converter.convertToEntityAttribute("PENDING");
        AccountType result4 = converter.convertToEntityAttribute("JACKPOT");
        
        // Assert
        assertEquals(AccountType.MainAccount.getInstance(), result1);
        assertEquals(AccountType.BonusAccount.getInstance(), result2);
        assertEquals(AccountType.PendingAccount.getInstance(), result3);
        assertEquals(AccountType.JackpotAccount.getInstance(), result4);
    }
    
    @Test
    void convertToEntityAttribute_ShouldThrowForInvalidString() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            converter.convertToEntityAttribute("INVALID_TYPE");
        });
        
        assertTrue(exception.getMessage().contains("Unknown account type"));
    }
} 