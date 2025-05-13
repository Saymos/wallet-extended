package com.cubeia.wallet.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity representing a wallet account with a balance.
 * The account maintains currency and type, with a mutable balance
 * that can only be updated through proper transaction mechanisms.
 * 
 * Note: H2Dialect is automatically detected by Hibernate and does not need
 * to be specified explicitly in application.properties.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal balance = BigDecimal.ZERO; // Initialize with zero
    
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;
    
    @Column(name = "account_type", nullable = false)
    @Convert(converter = AccountTypeConverter.class)
    private AccountType accountType;
    
    /**
     * Default no-args constructor required by JPA.
     * Not intended for direct use - use the parameterized constructor instead.
     */
    protected Account() {
        // Required by JPA, initialize with defaults
        this.currency = Currency.EUR;
        this.accountType = AccountType.MainAccount.getInstance();
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
    
    public UUID getId() {
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
     * Updates the balance - only accessible to entities in the same package
     * This ensures balance can only be modified through proper transaction mechanisms
     * 
     * @param newBalance the new balance to set
     */
    void updateBalance(BigDecimal newBalance) {
        this.balance = newBalance;
    }
    
    /**
     * Determines the maximum withdrawal amount based on account type.
     * Uses pattern matching for switch to handle different account types.
     * 
     * @return The maximum amount that can be withdrawn
     */
    public BigDecimal getMaxWithdrawalAmount() {
        return switch(accountType) {
            case AccountType.MainAccount mainAccount -> balance;
            case AccountType.BonusAccount bonusAccount -> BigDecimal.ZERO; // Cannot withdraw from bonus account
            case AccountType.PendingAccount pendingAccount -> BigDecimal.ZERO; // Cannot withdraw from pending
            case AccountType.JackpotAccount jackpotAccount -> balance; // Full withdrawal allowed
        };
    }
} 