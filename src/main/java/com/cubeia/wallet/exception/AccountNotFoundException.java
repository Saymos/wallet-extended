package com.cubeia.wallet.exception;

/**
 * Exception thrown when an account cannot be found.
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String message) {
        super(message);
    }

    public AccountNotFoundException(Long accountId) {
        super("Account not found with ID: " + accountId);
    }
} 