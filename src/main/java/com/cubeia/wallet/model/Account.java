package com.cubeia.wallet.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity representing a wallet account with a balance.
 * The account maintains an immutable currency and type, with a mutable balance
 * that can only be updated through proper transaction mechanisms.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal balance = BigDecimal.ZERO; // Initialize with zero
    
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private final Currency currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private final AccountType accountType;
    
    /**
     * Default no-args constructor required by JPA.
     * Not intended for direct use - use the parameterized constructor instead.
     */
    protected Account() {
        // Required by JPA, initialize with defaults
        this.currency = Currency.EUR;
        this.accountType = AccountType.MAIN;
    }
    
    /**
     * Create an account with a specific currency and account type.
     * 
     * @param currency The currency for this account
     * @param accountType The type of account
     */
    public Account(Currency currency, AccountType accountType) {
        this.currency = currency;
        this.accountType = accountType;
    }
    
    public Long getId() {
        return id;
    }
    
    public BigDecimal getBalance() {
        return balance;
    }
    
    public Currency getCurrency() {
        return currency;
    }
    
    public AccountType getAccountType() {
        return accountType;
    }
    
    /**
     * Updates the balance - only accessible to Transaction
     * 
     * @param newBalance the new balance to set
     */
    void updateBalance(BigDecimal newBalance) {
        this.balance = newBalance;
    }
} 