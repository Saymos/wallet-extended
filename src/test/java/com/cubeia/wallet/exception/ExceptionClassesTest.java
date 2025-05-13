package com.cubeia.wallet.exception;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        Long accountId = 42L;
        AccountNotFoundException exception = new AccountNotFoundException(accountId);
        
        // Assert
        assertEquals("Account not found with ID: " + accountId, exception.getMessage());
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
    void insufficientFundsException_WithAccountIdAndAmount() {
        // Act
        Long accountId = 42L;
        String amount = "100.00";
        InsufficientFundsException exception = new InsufficientFundsException(accountId, amount);
        
        // Assert
        assertEquals("Account with ID: " + accountId + " has insufficient funds for transfer of " + amount, 
                     exception.getMessage());
    }
    
    @Test
    void errorResponse_DefaultConstructor() {
        // Act
        ErrorResponse errorResponse = new ErrorResponse();
        
        // Assert
        assertNull(errorResponse.getTimestamp());
        assertEquals(0, errorResponse.getStatus());
        assertNull(errorResponse.getError());
        assertNull(errorResponse.getMessage());
        assertNull(errorResponse.getPath());
    }
    
    @Test
    void errorResponse_ParameterizedConstructor() {
        // Arrange
        LocalDateTime timestamp = LocalDateTime.now();
        int status = 404;
        String error = "Not Found";
        String message = "Resource not found";
        String path = "/api/resource";
        
        // Act
        ErrorResponse errorResponse = new ErrorResponse(timestamp, status, error, message, path);
        
        // Assert
        assertEquals(timestamp, errorResponse.getTimestamp());
        assertEquals(status, errorResponse.getStatus());
        assertEquals(error, errorResponse.getError());
        assertEquals(message, errorResponse.getMessage());
        assertEquals(path, errorResponse.getPath());
    }
    
    @Test
    void errorResponse_Setters() {
        // Arrange
        LocalDateTime timestamp = LocalDateTime.now();
        int status = 500;
        String error = "Internal Server Error";
        String message = "Something went wrong";
        String path = "/api/broken";
        
        // Act
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setTimestamp(timestamp);
        errorResponse.setStatus(status);
        errorResponse.setError(error);
        errorResponse.setMessage(message);
        errorResponse.setPath(path);
        
        // Assert
        assertEquals(timestamp, errorResponse.getTimestamp());
        assertEquals(status, errorResponse.getStatus());
        assertEquals(error, errorResponse.getError());
        assertEquals(message, errorResponse.getMessage());
        assertEquals(path, errorResponse.getPath());
    }
} 