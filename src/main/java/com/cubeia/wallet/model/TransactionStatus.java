package com.cubeia.wallet.model;

/**
 * Represents the status of a financial transaction in the wallet system.
 * Used to track the lifecycle of transactions from initiation to completion.
 */
public enum TransactionStatus {
    /**
     * Transaction has been created but not yet fully processed.
     * This is the initial state of a transaction.
     */
    PENDING,
    
    /**
     * Transaction has been successfully completed.
     * All ledger entries have been created and balances updated.
     */
    SUCCESS,
    
    /**
     * Transaction has failed to complete.
     * This could be due to insufficient funds, currency mismatch,
     * or other validation errors.
     */
    FAILED
} 