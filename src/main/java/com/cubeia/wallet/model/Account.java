package com.cubeia.wallet.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.GenericGenerator;
import org.springframework.beans.factory.annotation.Autowired;

import com.cubeia.wallet.service.DoubleEntryService;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

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
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;
    
    @Column(name = "account_type", nullable = false)
    @Convert(converter = AccountTypeConverter.class)
    private AccountType accountType;
    
    /**
     * The DoubleEntryService used to calculate balance from ledger entries.
     * This field is not persisted and is automatically injected in managed contexts.
     */
    @Transient
    private DoubleEntryService doubleEntryService;
    
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
    
    /**
     * Gets the current balance calculated from ledger entries using double-entry bookkeeping.
     * If the DoubleEntryService is not available, returns zero.
     * 
     * @return The calculated balance from ledger entries
     */
    public BigDecimal getBalance() {
        if (doubleEntryService == null || id == null) {
            return BigDecimal.ZERO;
        }
        return doubleEntryService.calculateBalance(id);
    }
    
    public Currency getCurrency() {
        return currency;
    }
    
    public AccountType getAccountType() {
        return accountType;
    }
    
    /**
     * Sets the DoubleEntryService to use for balance calculation.
     * This is automatically called by Spring in a managed context.
     * 
     * @param doubleEntryService The service to use for balance calculation
     */
    @Autowired
    public void setDoubleEntryService(DoubleEntryService doubleEntryService) {
        this.doubleEntryService = doubleEntryService;
    }
    
    /**
     * Determines the maximum withdrawal amount based on account type.
     * 
     * @return The maximum amount that can be withdrawn
     */
    public BigDecimal getMaxWithdrawalAmount() {
        // Main and Jackpot accounts allow full balance withdrawal
        if (accountType instanceof AccountType.MainAccount || 
            accountType instanceof AccountType.JackpotAccount) {
            return getBalance();
        }
        
        // Bonus and Pending accounts do not allow withdrawals
        return BigDecimal.ZERO;
    }
} 