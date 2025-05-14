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
import jakarta.persistence.Table;

/**
 * Entity representing a transaction between accounts.
 * This is a record of a financial transaction.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
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
    
    @Column(name = "reference", length = 255)
    private String reference;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
    
    /**
     * Default constructor.
     * Required by JPA, but should not be used directly in application code.
     */
    protected Transaction() {
        // Required by JPA
    }
    
    /**
     * Creates a new transaction with the specified details.
     *
     * @param fromAccountId The account ID from which funds are transferred
     * @param toAccountId The account ID to which funds are transferred
     * @param amount The amount of the transaction
     * @param transactionType The type of transaction
     * @param currency The currency of the transaction
     * @param reference An optional reference for the transaction
     */
    public Transaction(UUID fromAccountId, UUID toAccountId, BigDecimal amount, 
            TransactionType transactionType, Currency currency, String reference) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.transactionType = transactionType;
        this.currency = currency;
        this.reference = reference;
    }
    
    /**
     * Creates a new transaction with the specified details.
     *
     * @param fromAccountId The account ID from which funds are transferred
     * @param toAccountId The account ID to which funds are transferred
     * @param amount The amount of the transaction
     * @param transactionType The type of transaction
     * @param currency The currency of the transaction
     */
    public Transaction(UUID fromAccountId, UUID toAccountId, BigDecimal amount, 
            TransactionType transactionType, Currency currency) {
        this(fromAccountId, toAccountId, amount, transactionType, currency, null);
    }
    
    /**
     * @deprecated This method is maintained only for test compatibility.
     * In production code, use TransactionService.executeTransaction() instead.
     */
    @Deprecated
    public void execute(Transaction transaction, Account fromAccount, Account toAccount) {
        // Verify it's the same transaction
        if (!(transaction instanceof Transaction t && t == this)) {
            throw new IllegalArgumentException("Transaction parameter must be the same instance as 'this'");
        }
        
        // Verify account IDs match this transaction
        if (!fromAccount.getId().equals(fromAccountId) || !toAccount.getId().equals(toAccountId)) {
            throw new IllegalArgumentException(String.format(
                "Account IDs do not match transaction record: Expected fromAccount ID: %s, Actual: %s, "
                + "Expected toAccount ID: %s, Actual: %s",
                fromAccountId, fromAccount.getId(), toAccountId, toAccount.getId()));
        }
        
        // Verify currencies match
        if (fromAccount.getCurrency() != currency || toAccount.getCurrency() != currency) {
            throw new IllegalArgumentException(String.format(
                "Currency mismatch: Transaction and accounts must use the same currency. "
                + "Transaction currency: %s, From account currency: %s, To account currency: %s",
                currency, fromAccount.getCurrency(), toAccount.getCurrency()));
        }
        
        // Calculate the new balances
        BigDecimal fromNewBalance = fromAccount.getBalance().subtract(amount);
        
        // Verify sufficient funds in source account
        if (fromNewBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(String.format(
                "Insufficient funds in account: %s, Current balance: %s, Required amount: %s",
                fromAccountId, fromAccount.getBalance(), amount));
        }
        
        // Update the balances
        fromAccount.updateBalance(fromNewBalance);
        toAccount.updateBalance(toAccount.getBalance().add(amount));
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
} 