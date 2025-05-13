package com.cubeia.wallet.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity representing a transaction between accounts.
 * This is an immutable record of a financial transaction.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_account_id", nullable = false)
    private final Long fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private final Long toAccountId;

    @Column(precision = 19, scale = 4, nullable = false)
    private final BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private final TransactionType transactionType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private final Currency currency;
    
    @Column(name = "reference", length = 255)
    private final String reference;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
    
    /**
     * Default constructor.
     * Required by JPA, but should not be used directly in application code.
     */
    protected Transaction() {
        // Required by JPA
        this.fromAccountId = null;
        this.toAccountId = null;
        this.amount = null;
        this.transactionType = null;
        this.currency = null;
        this.reference = null;
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
    public Transaction(Long fromAccountId, Long toAccountId, BigDecimal amount, 
            TransactionType transactionType, Currency currency, String reference) {
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
        
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
    public Transaction(Long fromAccountId, Long toAccountId, BigDecimal amount, 
            TransactionType transactionType, Currency currency) {
        this(fromAccountId, toAccountId, amount, transactionType, currency, null);
    }
    
    /**
     * Executes this transaction by updating the account balances.
     * This method can be called with (fromAccount, toAccount) or with (transaction)
     * to guarantee that the transaction record and actual balance changes remain consistent.
     *
     * @param fromAccount The source account 
     * @param toAccount The destination account
     * @throws IllegalArgumentException if the transaction can't be executed
     */
    private void execute(Account fromAccount, Account toAccount) {
        // Verify account IDs match this transaction
        if (!fromAccount.getId().equals(fromAccountId) || !toAccount.getId().equals(toAccountId)) {
            throw new IllegalArgumentException("""
                Account IDs do not match transaction record:
                Expected fromAccount ID: %d, Actual: %d
                Expected toAccount ID: %d, Actual: %d
                """.formatted(fromAccountId, fromAccount.getId(), toAccountId, toAccount.getId()));
        }
        
        // Verify currencies match using pattern matching
        if (fromAccount.getCurrency() != currency || toAccount.getCurrency() != currency) {
            throw new IllegalArgumentException("""
                Currency mismatch: Transaction and accounts must use the same currency
                Transaction currency: %s
                From account currency: %s
                To account currency: %s
                """.formatted(currency, fromAccount.getCurrency(), toAccount.getCurrency()));
        }
        
        // Verify sufficient funds
        BigDecimal fromNewBalance = fromAccount.getBalance().subtract(amount);
        if (fromNewBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("""
                Insufficient funds in account: %d
                Current balance: %s
                Required amount: %s
                """.formatted(fromAccountId, fromAccount.getBalance(), amount));
        }
        
        // Execute the transaction
        fromAccount.updateBalance(fromNewBalance);
        toAccount.updateBalance(toAccount.getBalance().add(amount));
    }
    
    /**
     * Self-executes this transaction.
     * This method exists to support clear, intentional code like transaction.execute(transaction),
     * which makes it obvious that the transaction object being executed matches the record being saved.
     * 
     * @param transaction This transaction instance (should be the same as 'this')
     * @param fromAccount The source account
     * @param toAccount The destination account
     * @throws IllegalArgumentException if transaction parameter isn't the same as 'this'
     */
    public void execute(Transaction transaction, Account fromAccount, Account toAccount) {
        // Verify it's the same transaction using pattern matching
        if (!(transaction instanceof Transaction t && t == this)) {
            throw new IllegalArgumentException("Transaction parameter must be the same instance as 'this'");
        }

        if (!Objects.equals(fromAccount.getId(), fromAccountId) || !Objects.equals(toAccount.getId(), toAccountId)) {
            throw new IllegalArgumentException("""
                Account IDs do not match transaction record:
                Expected fromAccount ID: %d, Actual: %d
                Expected toAccount ID: %d, Actual: %d
                """.formatted(fromAccountId, fromAccount.getId(), toAccountId, toAccount.getId()));
        }
        
        // Execute normally
        execute(fromAccount, toAccount);
    }
    
    public Long getId() {
        return id;
    }
    
    public Long getFromAccountId() {
        return fromAccountId;
    }
    
    public Long getToAccountId() {
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