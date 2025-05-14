package com.cubeia.wallet.exception;

import java.util.UUID;

/**
 * Exception thrown when a transaction is not found.
 */
public class TransactionNotFoundException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    private final UUID transactionId;
    
    /**
     * Constructs a new transaction not found exception with the specified transaction ID.
     *
     * @param transactionId the ID of the transaction that was not found
     */
    public TransactionNotFoundException(UUID transactionId) {
        super("Transaction not found with ID: " + transactionId);
        this.transactionId = transactionId;
    }
    
    /**
     * Returns the ID of the transaction that was not found.
     *
     * @return the transaction ID
     */
    public UUID getTransactionId() {
        return transactionId;
    }
} 