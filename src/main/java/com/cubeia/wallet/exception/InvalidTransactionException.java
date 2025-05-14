package com.cubeia.wallet.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a transaction is invalid or cannot be processed.
 * This is used for validation errors that are not covered by more specific exceptions.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTransactionException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    private static final String INVALID_TRANSACTION_ID_TEMPLATE = "Invalid transaction with ID %s: %s";
    private static final String INVALID_TRANSACTION_TEMPLATE = "Invalid transaction: %s";
    private static final String DUPLICATE_REFERENCE_TEMPLATE = 
            "Transaction with reference ID '%s' already exists with different parameters";
    private static final String NON_POSITIVE_AMOUNT_MESSAGE = "Transaction amount must be positive";

    /**
     * Constructs a new exception with the specified transaction ID and reason.
     * 
     * @param transactionId the ID of the invalid transaction
     * @param reason additional information about the error
     */
    public InvalidTransactionException(UUID transactionId, String reason) {
        super(String.format(INVALID_TRANSACTION_ID_TEMPLATE, transactionId, reason));
    }

    /**
     * Constructs a new exception with a reason.
     * 
     * @param reason The reason the transaction is invalid
     */
    public InvalidTransactionException(String reason) {
        super(String.format(INVALID_TRANSACTION_TEMPLATE, reason));
    }
    
    /**
     * Creates an exception for a duplicate reference ID with different parameters.
     * 
     * @param referenceId The reference ID that already exists
     * @return A new exception instance
     */
    public static InvalidTransactionException forDuplicateReference(String referenceId) {
        return new InvalidTransactionException(String.format(DUPLICATE_REFERENCE_TEMPLATE, referenceId));
    }
    
    /**
     * Creates an exception for a non-positive transaction amount.
     * 
     * @return A new exception instance
     */
    public static InvalidTransactionException forNonPositiveAmount() {
        return new InvalidTransactionException(NON_POSITIVE_AMOUNT_MESSAGE);
    }
} 