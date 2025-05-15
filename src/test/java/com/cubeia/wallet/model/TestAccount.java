package com.cubeia.wallet.model;

import jakarta.persistence.Entity;

/**
 * Special Account implementation for testing that allows any account type to withdraw funds.
 * This is needed for double-entry integrity tests where we need to validate the system as a whole.
 */
@Entity
public class TestAccount extends Account {
    
    /**
     * Create a test account with a specific currency and account type.
     * 
     * @param currency The currency for this account
     * @param accountType The type of account
     */
    public TestAccount(Currency currency, AccountType accountType) {
        super(currency, accountType);
    }
    
    /**
     * Default constructor required by JPA.
     */
    protected TestAccount() {
        super();
    }
} 