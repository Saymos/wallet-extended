package com.cubeia.wallet.model;

/**
 * Represents the type of ledger entry in a double-entry bookkeeping system.
 * <p>
 * In double-entry bookkeeping, each transaction must have at least one DEBIT and one CREDIT
 * entry, and the sum of all DEBIT amounts must equal the sum of all CREDIT amounts.
 * </p>
 */
public enum EntryType {
    /**
     * Represents a debit entry, which typically reduces an account's balance.
     * For asset accounts, debits increase the balance.
     * For liability and equity accounts, debits decrease the balance.
     */
    DEBIT,
    
    /**
     * Represents a credit entry, which typically increases an account's balance.
     * For asset accounts, credits decrease the balance.
     * For liability and equity accounts, credits increase the balance.
     */
    CREDIT
} 