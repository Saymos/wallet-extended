package com.cubeia.wallet.service;

import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.Transaction;

/**
 * Container for validation results.
 * Includes the source and destination accounts and any existing transaction
 * found during idempotency checks.
 */
public record TransferValidationResult(
        Account fromAccount, 
        Account toAccount, 
        Transaction existingTransaction) {
    
    /**
     * Constructor that defaults existingTransaction to null.
     * Provided for backward compatibility.
     */
    public TransferValidationResult(Account fromAccount, Account toAccount) {
        this(fromAccount, toAccount, null);
    }
} 