package com.cubeia.wallet.exception;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.cubeia.wallet.model.Currency;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        webRequest = mock(WebRequest.class);
    }

    @Test
    void handleAccountNotFoundException() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        AccountNotFoundException ex = new AccountNotFoundException(accountId);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccountNotFoundException(ex, webRequest);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("Account not found"));
        assertTrue(response.getBody().getMessage().contains(accountId.toString()));
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void handleInsufficientFundsException() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        String reason = "Insufficient balance";
        InsufficientFundsException ex = new InsufficientFundsException(accountId, reason);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInsufficientFundsException(ex, webRequest);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("Insufficient funds"));
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void handleCurrencyMismatchException() {
        // Arrange
        CurrencyMismatchException ex = new CurrencyMismatchException(Currency.EUR, Currency.USD);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleCurrencyMismatchException(ex, webRequest);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("Currency mismatch: Expected EUR, but got USD", response.getBody().getMessage());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void handleInvalidTransactionException() {
        // Arrange
        String reason = "Amount must be positive";
        InvalidTransactionException ex = new InvalidTransactionException(reason);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidTransactionException(ex, webRequest);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("Invalid transaction: " + reason, response.getBody().getMessage());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void handleInvalidTransactionExceptionWithId() {
        // Arrange
        UUID transactionId = UUID.randomUUID();
        String reason = "Invalid parameters";
        InvalidTransactionException ex = new InvalidTransactionException(transactionId, reason);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidTransactionException(ex, webRequest);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("Invalid transaction with ID " + transactionId + ": " + reason, response.getBody().getMessage());
        assertNotNull(response.getBody().getTimestamp());
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
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("Validation failed", response.getBody().getMessage());
        assertNotNull(response.getBody().getFieldErrors());
        assertTrue(response.getBody().getFieldErrors().containsKey("amount"));
        assertEquals("must be positive", response.getBody().getFieldErrors().get("amount"));
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void handleIllegalArgumentException() {
        // Arrange
        IllegalArgumentException ex = new IllegalArgumentException("Illegal argument");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgumentException(ex, webRequest);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("Illegal argument", response.getBody().getMessage());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void handleTypeMismatch() {
        // Arrange
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("paramName");
        when(ex.getValue()).thenReturn("invalidValue");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleTypeMismatch(ex, webRequest);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("paramName"));
        assertTrue(response.getBody().getMessage().contains("invalidValue"));
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void handleHttpMessageNotReadable() {
        // Arrange
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("Invalid JSON");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleHttpMessageNotReadable(ex, webRequest);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("Malformed JSON"));
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void handleGlobalException() {
        // Arrange
        Exception ex = new Exception("Unknown error");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGlobalException(ex, webRequest);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getBody().getStatus());
        assertEquals("An unexpected error occurred", response.getBody().getMessage());
        assertNotNull(response.getBody().getTimestamp());
    }
} 