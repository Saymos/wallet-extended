package com.cubeia.wallet.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an account is not found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AccountNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the specified account ID.
     * 
     * @param accountId the ID of the account that was not found
     */
    public AccountNotFoundException(UUID accountId) {
        super("Account not found with id: " + accountId);
    }

    /**
     * Constructs a new exception with the specified message.
     * 
     * @param message the detail message
     */
    public AccountNotFoundException(String message) {
        super(message);
    }
} 