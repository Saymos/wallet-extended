package com.cubeia.wallet.exception;

/**
 * Exception thrown when an account has insufficient funds for a transfer.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }

    public InsufficientFundsException(Long accountId, String amount) {
        super("Account with ID: " + accountId + " has insufficient funds for transfer of " + amount);
    }
} 