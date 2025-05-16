package com.cubeia.wallet.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Global exception handler to convert exceptions to appropriate HTTP responses.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ErrorResponse buildErrorResponse(HttpStatus status, String message, WebRequest request) {
        return new ErrorResponse(status.value(), message, LocalDateTime.now(), null);
    }

    /**
     * Handle AccountNotFoundException, return 404 Not Found.
     */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFoundException(
            AccountNotFoundException ex) {
        return new ResponseEntity<>(
            buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), null),
            HttpStatus.NOT_FOUND);
    }

    /**
     * Handle InsufficientFundsException, return 400 Bad Request.
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFundsException(
            InsufficientFundsException ex) {
        return new ResponseEntity<>(
            buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), null),
            HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle CurrencyMismatchException, return 400 Bad Request.
     */
    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ErrorResponse> handleCurrencyMismatchException(
            CurrencyMismatchException ex) {
        return new ResponseEntity<>(
            buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), null),
            HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle InvalidTransactionException, return 400 Bad Request.
     */
    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransactionException(
            InvalidTransactionException ex) {
        return new ResponseEntity<>(
            buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), null),
            HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle IllegalArgumentException, return 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex) {
        return new ResponseEntity<>(
            buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), null),
            HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle validation errors from @Valid annotation, return 400 Bad Request.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        ValidationErrorResponse validationError = new ValidationErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Validation failed",
            LocalDateTime.now(),
            errors
        );
        // Set path via reflection if needed (or extend ValidationErrorResponse if desired)
        return new ResponseEntity<>(validationError, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle type mismatch exceptions.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        String msg = "Parameter '" + ex.getName() + "' has invalid value: " + ex.getValue();
        return new ResponseEntity<>(
            buildErrorResponse(HttpStatus.BAD_REQUEST, msg, null),
            HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle JSON parse exceptions.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex) {
        String msg = "Malformed JSON request: " + ex.getMessage();
        return new ResponseEntity<>(
            buildErrorResponse(HttpStatus.BAD_REQUEST, msg, null),
            HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle general exceptions, return 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex) {
        log.error("Unhandled exception", ex);
        return new ResponseEntity<>(
            buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null),
            HttpStatus.INTERNAL_SERVER_ERROR);
    }
} 