package com.cubeia.wallet.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Entity representing a financial transaction in the wallet system.
 */
@Entity
@Table(
    name = "transactions",
    indexes = {
        @Index(name = "idx_transaction_from_account", columnList = "from_account_id"),
        @Index(name = "idx_transaction_to_account", columnList = "to_account_id"),
        @Index(name = "idx_transaction_reference", columnList = "reference")
    }
)
public class Transaction {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "uuid2")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private UUID toAccountId;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;
    
    @Column(name = "reference", length = 255, unique = true)
    private String reference;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status = TransactionStatus.PENDING;
    
    @Column(length = 255)
    private String description;
    
    @Column(length = 255)
    private String failureReason;
    
    /**
     * Default constructor.
     * Required by JPA, but should not be used directly in application code.
     */
    protected Transaction() {
        // Required by JPA
    }
    
    /**
     * Creates a new transaction with the specified parameters.
     *
     * @param id the transaction ID (can be null for auto-generation)
     * @param fromAccountId the ID of the source account
     * @param toAccountId the ID of the destination account
     * @param amount the transaction amount
     * @param type the transaction type
     * @param currency the transaction currency
     * @param reference optional reference ID for idempotency
     * @param description optional description of the transaction
     * @throws IllegalArgumentException if any required parameter is invalid
     */
    public Transaction(UUID id, UUID fromAccountId, UUID toAccountId, BigDecimal amount, TransactionType type, 
                       Currency currency, String reference, String description) {
        if (fromAccountId == null) {
            throw new IllegalArgumentException("Source account ID cannot be null");
        }
        if (toAccountId == null) {
            throw new IllegalArgumentException("Destination account ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (type == null) {
            throw new IllegalArgumentException("Transaction type cannot be null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
        
        this.id = id;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.transactionType = type;
        this.currency = currency;
        this.reference = reference;
        this.description = description;
        this.timestamp = LocalDateTime.now();
        this.status = TransactionStatus.PENDING;
    }
    
    /**
     * Creates a new transaction with the specified parameters.
     *
     * @param fromAccountId the ID of the source account
     * @param toAccountId the ID of the destination account
     * @param amount the transaction amount
     * @param type the transaction type
     * @param currency the transaction currency
     * @throws IllegalArgumentException if any required parameter is invalid
     */
    public Transaction(UUID fromAccountId, UUID toAccountId, BigDecimal amount, TransactionType type, Currency currency) {
        this(null, fromAccountId, toAccountId, amount, type, currency, null, null);
    }
    
    /**
     * Creates a new transaction with the specified parameters and reference ID.
     *
     * @param fromAccountId the ID of the source account
     * @param toAccountId the ID of the destination account
     * @param amount the transaction amount
     * @param type the transaction type
     * @param currency the transaction currency
     * @param reference optional reference ID for idempotency
     * @throws IllegalArgumentException if any required parameter is invalid
     */
    public Transaction(UUID fromAccountId, UUID toAccountId, BigDecimal amount, TransactionType type, Currency currency, String reference) {
        this(null, fromAccountId, toAccountId, amount, type, currency, reference, null);
    }
    
    /**
     * Creates a new transaction with the specified parameters, reference ID, and description.
     *
     * @param fromAccountId the ID of the source account
     * @param toAccountId the ID of the destination account
     * @param amount the transaction amount
     * @param type the transaction type
     * @param currency the transaction currency
     * @param reference optional reference ID for idempotency
     * @param description optional description of the transaction
     * @throws IllegalArgumentException if any required parameter is invalid
     */
    public Transaction(UUID fromAccountId, UUID toAccountId, BigDecimal amount, TransactionType type, 
                       Currency currency, String reference, String description) {
        this(null, fromAccountId, toAccountId, amount, type, currency, reference, description);
    }
    
    public UUID getId() {
        return id;
    }
    
    public UUID getFromAccountId() {
        return fromAccountId;
    }
    
    public UUID getToAccountId() {
        return toAccountId;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public TransactionType getTransactionType() {
        return transactionType;
    }
    
    public Currency getCurrency() {
        return currency;
    }
    
    public String getReference() {
        return reference;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public TransactionStatus getStatus() {
        return status;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
    
    /**
     * Marks the transaction as successful.
     */
    public void markSuccess() {
        this.status = TransactionStatus.SUCCESS;
    }
    
    /**
     * Marks the transaction as failed with a reason.
     * 
     * @param reason the reason for failure
     */
    public void markFailed(String reason) {
        this.status = TransactionStatus.FAILED;
        this.failureReason = reason;
    }
    
    /**
     * Sets the description for this transaction.
     * 
     * @param description the transaction description
     */
    public void setDescription(String description) {
        this.description = description;
    }
} 