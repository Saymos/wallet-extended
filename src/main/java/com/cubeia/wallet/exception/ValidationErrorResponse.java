package com.cubeia.wallet.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Error response format for validation errors, includes field-specific error details.
 */
public class ValidationErrorResponse extends ErrorResponse {
    private final Map<String, String> fieldErrors;

    public ValidationErrorResponse(int status, String message, LocalDateTime timestamp, Map<String, String> fieldErrors) {
        super(status, message, timestamp);
        this.fieldErrors = fieldErrors;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
} 