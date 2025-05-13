package com.cubeia.wallet.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an account has insufficient funds for a transaction.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InsufficientFundsException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs a new exception with the specified account ID and reason.
     * 
     * @param accountId the ID of the account with insufficient funds
     * @param reason additional information about the error
     */
    public InsufficientFundsException(UUID accountId, String reason) {
        super("Insufficient funds in account " + accountId + ": " + reason);
    }
    
    /**
     * Constructs a new exception with the specified message.
     * 
     * @param message the detail message
     */
    public InsufficientFundsException(String message) {
        super(message);
    }
} 