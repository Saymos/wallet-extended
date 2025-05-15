package com.cubeia.wallet.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Represents an immutable ledger entry in the double-entry bookkeeping system.
 * 
 * An immutable record of a DEBIT or CREDIT operation on an account. Every financial
 * transaction creates balanced pairs of ledger entries to maintain the double-entry
 * bookkeeping principle that the sum of debits equals the sum of credits.
 * 
 * See README.md "Double-Entry Bookkeeping Implementation" section for more details
 * about the double-entry system architecture and benefits.
 * 
 * @see EntryType
 * @see Transaction
 */
@Entity
@Table(name = "ledger_entries",
    indexes = {
        @Index(name = "idx_ledger_entry_account", columnList = "accountId")
    }
)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull
    @Column(nullable = false)
    private UUID accountId;

    @NotNull
    @Column(nullable = false)
    private UUID transactionId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType entryType;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column
    private String description;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    /**
     * Default constructor for JPA.
     * Private to enforce the use of the builder for instantiation.
     */
    protected LedgerEntry() {
        // Required by JPA
    }

    /**
     * Creates a new LedgerEntry with the specified values.
     * Private constructor to enforce immutability through the builder.
     */
    private LedgerEntry(UUID accountId, UUID transactionId, EntryType entryType, 
                        BigDecimal amount, String description, Currency currency) {
        this.accountId = accountId;
        this.transactionId = transactionId;
        this.entryType = entryType;
        // Ensure amount is always positive in the DB, the sign is determined by entryType
        this.amount = amount.abs();
        this.description = description;
        this.timestamp = LocalDateTime.now();
        this.currency = currency;
    }

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    // Getters for all fields to maintain immutability
    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getDescription() {
        return description;
    }
    
    public Currency getCurrency() {
        return currency;
    }

    /**
     * Gets the signed amount based on the entry type.
     * DEBIT entries are represented with negative values for user accounts,
     * CREDIT entries are represented with positive values.
     *
     * @return the signed amount based on entry type
     */
    public BigDecimal getSignedAmount() {
        return entryType == EntryType.DEBIT ? amount.negate() : amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LedgerEntry that = (LedgerEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "LedgerEntry{" +
                "id=" + id +
                ", accountId=" + accountId +
                ", transactionId=" + transactionId +
                ", entryType=" + entryType +
                ", amount=" + amount +
                ", timestamp=" + timestamp +
                ", description='" + description + '\'' +
                ", currency=" + currency +
                '}';
    }

    /**
     * Builder class for creating immutable LedgerEntry instances.
     */
    public static class Builder {
        private UUID accountId;
        private UUID transactionId;
        private EntryType entryType;
        private BigDecimal amount;
        private String description;
        private Currency currency;

        public Builder accountId(UUID accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder transactionId(UUID transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder entryType(EntryType entryType) {
            this.entryType = entryType;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder currency(Currency currency) {
            this.currency = currency;
            return this;
        }

        public LedgerEntry build() {
            validate();
            return new LedgerEntry(accountId, transactionId, entryType, amount, description, currency);
        }

        private void validate() {
            if (accountId == null) {
                throw new IllegalArgumentException("Account ID cannot be null");
            }
            if (transactionId == null) {
                throw new IllegalArgumentException("Transaction ID cannot be null");
            }
            if (entryType == null) {
                throw new IllegalArgumentException("Entry type cannot be null");
            }
            if (amount == null) {
                throw new IllegalArgumentException("Amount cannot be null");
            }
            if (amount.abs().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }
            if (currency == null) {
                throw new IllegalArgumentException("Currency cannot be null");
            }
        }
    }

    /**
     * Creates a builder for constructing LedgerEntry instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
} 