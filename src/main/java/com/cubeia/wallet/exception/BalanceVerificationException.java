package com.cubeia.wallet.exception;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Exception thrown when an account's calculated balance doesn't match the expected balance.
 * <p>
 * This is typically used during balance reconciliation or data integrity checks.
 * </p>
 */
public class BalanceVerificationException extends RuntimeException {
    
    private final UUID accountId;
    private final BigDecimal expectedBalance;
    private final BigDecimal actualBalance;
    
    /**
     * Constructs a new balance verification exception with the specified account and balance details.
     *
     * @param accountId the ID of the account with the balance discrepancy
     * @param expectedBalance the expected balance value
     * @param actualBalance the actual calculated balance value
     */
    public BalanceVerificationException(UUID accountId, BigDecimal expectedBalance, BigDecimal actualBalance) {
        super(String.format("Balance verification failed for account %s: expected %s but calculated %s", 
                accountId, expectedBalance, actualBalance));
        this.accountId = accountId;
        this.expectedBalance = expectedBalance;
        this.actualBalance = actualBalance;
    }
    
    /**
     * Gets the ID of the account with the balance discrepancy.
     *
     * @return the account ID
     */
    public UUID getAccountId() {
        return accountId;
    }
    
    /**
     * Gets the expected balance value.
     *
     * @return the expected balance
     */
    public BigDecimal getExpectedBalance() {
        return expectedBalance;
    }
    
    /**
     * Gets the actual calculated balance value.
     *
     * @return the actual balance
     */
    public BigDecimal getActualBalance() {
        return actualBalance;
    }
    
    /**
     * Calculates the discrepancy amount (actual - expected).
     *
     * @return the discrepancy amount
     */
    public BigDecimal getDiscrepancy() {
        return actualBalance.subtract(expectedBalance);
    }
} 