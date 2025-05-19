package com.cubeia.wallet.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Entity representing a wallet account.
 * The account maintains currency and type, while the balance is calculated
 * from ledger entries using double-entry bookkeeping.
 * 
 * Note: H2Dialect is automatically detected by Hibernate and does not need
 * to be specified explicitly in application.properties.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private final Currency currency;
    
    @Column(name = "account_type", nullable = false)
    @Convert(converter = AccountTypeConverter.class)
    private final AccountType accountType;
    
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
    
    /**
     * Special constructor for system accounts with fixed ID.
     * 
     * @param id The predefined UUID for the account
     * @param currency The currency for this account
     * @param accountType The type of account
     */
    public Account(UUID id, Currency currency, AccountType accountType) {
        this.id = id;
        this.currency = currency;
        this.accountType = accountType;
    }

    // Add PrePersist method to handle ID assignment
    @PrePersist
    void onPrePersist() {
        // Only generate an ID if one hasn't been explicitly set
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
    
    public UUID getId() {
        return id;
    }
    
    public Currency getCurrency() {
        return currency;
    }
    
    public AccountType getAccountType() {
        return accountType;
    }
} 