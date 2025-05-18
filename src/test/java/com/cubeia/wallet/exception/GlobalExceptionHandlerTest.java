package com.cubeia.wallet.exception;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.cubeia.wallet.model.Currency;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void handleAccountNotFoundException() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        AccountNotFoundException ex = new AccountNotFoundException(accountId);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccountNotFoundException(ex);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody(), "Response body should not be null");
        
        // Store response body in local variable to avoid potential NPE
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse, "Error response should not be null");
        
        assertEquals(HttpStatus.NOT_FOUND.value(), errorResponse.getStatus());
        assertTrue(errorResponse.getMessage().contains("Account not found"));
        assertTrue(errorResponse.getMessage().contains(accountId.toString()));
        assertNotNull(errorResponse.getTimestamp(), "Timestamp should not be null");
    }

    @Test
    void handleInsufficientFundsException() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        String reason = "Insufficient balance";
        InsufficientFundsException ex = new InsufficientFundsException(accountId, reason);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInsufficientFundsException(ex);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody(), "Response body should not be null");
        
        // Store response body in local variable to avoid potential NPE
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse, "Error response should not be null");
        
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertTrue(errorResponse.getMessage().contains("Insufficient funds"));
        assertNotNull(errorResponse.getTimestamp(), "Timestamp should not be null");
    }

    @Test
    void handleCurrencyMismatchException() {
        // Arrange
        CurrencyMismatchException ex = new CurrencyMismatchException(Currency.EUR, Currency.USD);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleCurrencyMismatchException(ex);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody(), "Response body should not be null");
        
        // Store response body in local variable to avoid potential NPE
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse, "Error response should not be null");
        
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertEquals("Currency mismatch: Expected EUR, but got USD", errorResponse.getMessage());
        assertNotNull(errorResponse.getTimestamp(), "Timestamp should not be null");
    }

    @Test
    void handleInvalidTransactionException() {
        // Arrange
        String reason = "Amount must be positive";
        InvalidTransactionException ex = new InvalidTransactionException(reason);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidTransactionException(ex);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody(), "Response body should not be null");
        
        // Store response body in local variable to avoid potential NPE
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse, "Error response should not be null");
        
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertEquals("Invalid transaction: " + reason, errorResponse.getMessage());
        assertNotNull(errorResponse.getTimestamp(), "Timestamp should not be null");
    }

    @Test
    void handleInvalidTransactionExceptionWithId() {
        // Arrange
        UUID transactionId = UUID.randomUUID();
        String reason = "Invalid parameters";
        InvalidTransactionException ex = new InvalidTransactionException(transactionId, reason);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidTransactionException(ex);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody(), "Response body should not be null");
        
        // Store response body in local variable to avoid potential NPE
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse, "Error response should not be null");
        
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertEquals("Invalid transaction with ID " + transactionId + ": " + reason, errorResponse.getMessage());
        assertNotNull(errorResponse.getTimestamp(), "Timestamp should not be null");
    }

    @Test
    void handleValidationExceptions() {
        // Arrange
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        
        List<FieldError> fieldErrors = new ArrayList<>();
        fieldErrors.add(new FieldError("object", "amount", "must be positive"));
        when(bindingResult.getAllErrors()).thenReturn(new ArrayList<>(fieldErrors));

        // Act
        ResponseEntity<ValidationErrorResponse> response = exceptionHandler.handleValidationExceptions(ex);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody(), "Response body should not be null");
        
        // Store response body in local variable to avoid potential NPE
        ValidationErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse, "Error response should not be null");
        
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertEquals("Validation failed", errorResponse.getMessage());
        assertNotNull(errorResponse.getFieldErrors(), "Field errors should not be null");
        assertTrue(errorResponse.getFieldErrors().containsKey("amount"));
        assertEquals("must be positive", errorResponse.getFieldErrors().get("amount"));
        assertNotNull(errorResponse.getTimestamp(), "Timestamp should not be null");
    }

    @Test
    void handleIllegalArgumentException() {
        // Arrange
        IllegalArgumentException ex = new IllegalArgumentException("Illegal argument");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgumentException(ex);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody(), "Response body should not be null");
        
        // Store response body in local variable to avoid potential NPE
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse, "Error response should not be null");
        
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertEquals("Illegal argument", errorResponse.getMessage());
        assertNotNull(errorResponse.getTimestamp(), "Timestamp should not be null");
    }

    @Test
    void handleTypeMismatch() {
        // Arrange
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("paramName");
        when(ex.getValue()).thenReturn("invalidValue");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleTypeMismatch(ex);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody(), "Response body should not be null");
        
        // Store response body in local variable to avoid potential NPE
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse, "Error response should not be null");
        
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertTrue(errorResponse.getMessage().contains("paramName"));
        assertTrue(errorResponse.getMessage().contains("invalidValue"));
        assertNotNull(errorResponse.getTimestamp(), "Timestamp should not be null");
    }

    @Test
    void handleHttpMessageNotReadable() {
        // Arrange
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("Invalid JSON");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleHttpMessageNotReadable(ex);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody(), "Response body should not be null");
        
        // Store response body in local variable to avoid potential NPE
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse, "Error response should not be null");
        
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertTrue(errorResponse.getMessage().contains("Malformed JSON"));
        assertNotNull(errorResponse.getTimestamp(), "Timestamp should not be null");
    }

    @Test
    void handleGlobalException() {
        // Arrange
        Exception ex = new Exception("Unknown error");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGlobalException(ex);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody(), "Response body should not be null");
        
        // Store response body in local variable to avoid potential NPE
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse, "Error response should not be null");
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), errorResponse.getStatus());
        assertEquals("An unexpected error occurred", errorResponse.getMessage());
        assertNotNull(errorResponse.getTimestamp(), "Timestamp should not be null");
    }
} 