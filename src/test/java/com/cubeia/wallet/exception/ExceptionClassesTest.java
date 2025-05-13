package com.cubeia.wallet.exception;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ExceptionClassesTest {

    @Test
    void accountNotFoundException_WithMessage() {
        // Act
        String message = "Custom error message";
        AccountNotFoundException exception = new AccountNotFoundException(message);
        
        // Assert
        assertEquals(message, exception.getMessage());
    }
    
    @Test
    void accountNotFoundException_WithAccountId() {
        // Act
        UUID accountId = UUID.randomUUID();
        AccountNotFoundException exception = new AccountNotFoundException(accountId);
        
        // Assert
        assertEquals("Account not found with id: " + accountId, exception.getMessage());
    }
    
    @Test
    void insufficientFundsException_WithMessage() {
        // Act
        String message = "Not enough money";
        InsufficientFundsException exception = new InsufficientFundsException(message);
        
        // Assert
        assertEquals(message, exception.getMessage());
    }
    
    @Test
    void insufficientFundsException_WithAccountIdAndReason() {
        // Act
        UUID accountId = UUID.randomUUID();
        String reason = "Balance too low";
        InsufficientFundsException exception = new InsufficientFundsException(accountId, reason);
        
        // Assert
        assertEquals("Insufficient funds in account " + accountId + ": " + reason, 
                     exception.getMessage());
    }
    
    @Test
    void errorResponse_Constructor() {
        // Arrange
        int status = 404;
        String message = "Not Found";
        LocalDateTime timestamp = LocalDateTime.now();
        
        // Act
        ErrorResponse errorResponse = new ErrorResponse(status, message, timestamp);
        
        // Assert
        assertEquals(timestamp, errorResponse.getTimestamp());
        assertEquals(status, errorResponse.getStatus());
        assertEquals(message, errorResponse.getMessage());
    }
    
    @Test
    void validationErrorResponse_Constructor() {
        // Arrange
        int status = 400;
        String message = "Validation Failed";
        LocalDateTime timestamp = LocalDateTime.now();
        java.util.Map<String, String> fieldErrors = new java.util.HashMap<>();
        fieldErrors.put("amount", "must be positive");
        
        // Act
        ValidationErrorResponse response = new ValidationErrorResponse(
            status, message, timestamp, fieldErrors);
        
        // Assert
        assertEquals(timestamp, response.getTimestamp());
        assertEquals(status, response.getStatus());
        assertEquals(message, response.getMessage());
        assertEquals(fieldErrors, response.getFieldErrors());
        assertEquals("must be positive", response.getFieldErrors().get("amount"));
    }
} 