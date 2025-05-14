package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.exception.InsufficientFundsException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.TransactionRepository;

/**
 * Service for managing account transactions.
 */
@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionTemplate transactionTemplate;

    public TransactionService(
            AccountRepository accountRepository, 
            TransactionRepository transactionRepository,
            PlatformTransactionManager transactionManager) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        
        // Initialize TransactionTemplate with appropriate settings
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    /**
     * Transfers funds between accounts with deadlock prevention.
     * 
     * Accounts are always locked in a consistent order (by comparing IDs) to prevent deadlocks.
     * See README for a detailed explanation of the deadlock prevention strategy.
     * 
     * @param fromAccountId The ID of the account to transfer from
     * @param toAccountId The ID of the account to transfer to
     * @param amount The amount to transfer
     * @return The created Transaction record
     * @throws AccountNotFoundException if either account is not found
     * @throws InsufficientFundsException if the from account has insufficient funds
     * @throws IllegalArgumentException if the amount is not positive or currencies don't match
     */
    public Transaction transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
        return transfer(fromAccountId, toAccountId, amount, null);
    }

    /**
     * Transfers funds between accounts with deadlock prevention and idempotency support.
     * 
     * If a transaction with the given reference ID already exists, it returns that transaction
     * instead of creating a new one, ensuring idempotency for duplicate requests.
     * 
     * Accounts are always locked in a consistent order (by comparing IDs) to prevent deadlocks.
     * See README for a detailed explanation of the deadlock prevention strategy.
     * 
     * @param fromAccountId The ID of the account to transfer from
     * @param toAccountId The ID of the account to transfer to
     * @param amount The amount to transfer
     * @param referenceId Optional reference ID for idempotency (can be null)
     * @return The created or existing Transaction record
     * @throws AccountNotFoundException if either account is not found
     * @throws InsufficientFundsException if the from account has insufficient funds
     * @throws IllegalArgumentException if a transaction with the same reference ID exists with different parameters,
     *                                  the amount is not positive, or currencies don't match
     */
    public Transaction transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String referenceId) {
        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
        
        // Check for existing transaction with the same reference ID - this doesn't need to be in a transaction
        if (referenceId != null && !referenceId.isEmpty()) {
            Optional<Transaction> existingTransaction = transactionRepository.findByReference(referenceId);
            if (existingTransaction.isPresent()) {
                Transaction existing = existingTransaction.get();
                
                // Verify that transaction parameters match
                if (!existing.getFromAccountId().equals(fromAccountId) ||
                    !existing.getToAccountId().equals(toAccountId) ||
                    existing.getAmount().compareTo(amount) != 0) {
                    throw new IllegalArgumentException(
                        "Transaction with reference ID '" + referenceId + "' already exists with different parameters"
                    );
                }
                
                // Return the existing transaction for idempotency
                return existing;
            }
        }
        
        // Pre-validation: Verify accounts exist
        Account fromAccount = accountRepository.findById(fromAccountId)
            .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        Account toAccount = accountRepository.findById(toAccountId)
            .orElseThrow(() -> new AccountNotFoundException(toAccountId));
        
        // Pre-validation: Check for currency mismatch (doesn't need a lock)
        if (!fromAccount.getCurrency().equals(toAccount.getCurrency())) {
            throw new IllegalArgumentException(String.format(
                "Currency mismatch: Cannot transfer between accounts with different currencies (%s and %s)",
                fromAccount.getCurrency(), toAccount.getCurrency()));
        }
        
        // Pre-validation: Check for sufficient funds
        // This is a preliminary check that will be verified again within the transaction
        // but helps fail fast before acquiring locks
        BigDecimal maxWithdrawal = fromAccount.getMaxWithdrawalAmount();
        if (amount.compareTo(maxWithdrawal) > 0) {
            String reason = String.format(
                "Amount %s exceeds maximum withdrawal amount %s for account type %s",
                amount, maxWithdrawal, fromAccount.getAccountType());
            throw new InsufficientFundsException(fromAccountId, reason);
        }
        
        // Pre-create the transaction object outside the transaction boundary
        Currency currency = fromAccount.getCurrency();
        Transaction transaction = new Transaction(
            fromAccountId, 
            toAccountId, 
            amount, 
            TransactionType.TRANSFER, 
            currency,
            referenceId
        );
        
        // Execute only the critical section within a transaction
        return transactionTemplate.execute(status -> {
            // Execute the transaction using our method
            // This handles account locking, validation, and balance updates
            executeTransaction(transaction);
            
            // Save and return the transaction record
            return transactionRepository.save(transaction);
        });
    }
    
    /**
     * Executes a transaction by updating account balances.
     * This method guarantees that balance updates are always tied to a transaction record.
     * All data for the transaction comes from the transaction object itself.
     * 
     * @param transaction The transaction record containing details of the transfer
     * @throws AccountNotFoundException if either account is not found
     * @throws InsufficientFundsException if the source account has insufficient funds
     * @throws IllegalArgumentException if validation fails (e.g., currency mismatch)
     */
    protected void executeTransaction(Transaction transaction) {
        UUID fromAccountId = transaction.getFromAccountId();
        UUID toAccountId = transaction.getToAccountId();
        BigDecimal amount = transaction.getAmount();
        Currency currency = transaction.getCurrency();
        
        // Always acquire locks in the same order to prevent deadlocks
        Account fromAccount;
        Account toAccount;
        
        // Determine lock order based on account IDs
        if (fromAccountId.compareTo(toAccountId) <= 0) {
            // Regular order: fromAccount has lower or equal ID
            fromAccount = accountRepository.findByIdWithLock(fromAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
            toAccount = accountRepository.findByIdWithLock(toAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(toAccountId));
        } else {
            // Reversed order: toAccount has lower ID
            toAccount = accountRepository.findByIdWithLock(toAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(toAccountId));
            fromAccount = accountRepository.findByIdWithLock(fromAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        }
        
        // Verify currencies match (both accounts and transaction must have same currency)
        if (fromAccount.getCurrency() != currency || toAccount.getCurrency() != currency) {
            throw new IllegalArgumentException(String.format(
                "Currency mismatch: Transaction and accounts must use the same currency. "
                + "Transaction currency: %s, From account currency: %s, To account currency: %s",
                currency, fromAccount.getCurrency(), toAccount.getCurrency()));
        }
        
        // Calculate new balances based on transaction amount
        BigDecimal fromNewBalance = fromAccount.getBalance().subtract(amount);
        
        // Verify sufficient funds in source account
        if (fromNewBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientFundsException(fromAccount.getId(), String.format(
                "Insufficient funds in account: %s, Current balance: %s, Required amount: %s",
                fromAccount.getId(), fromAccount.getBalance(), amount));
        }
        
        // Update the balances
        fromAccount.updateBalance(fromNewBalance);
        toAccount.updateBalance(toAccount.getBalance().add(amount));
        
        // Save the updated accounts
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
    }
    
    /**
     * Get transaction history for an account.
     * 
     * @param accountId The account ID to get transactions for
     * @return List of transactions involving the account
     */
    public List<Transaction> getAccountTransactions(UUID accountId) {
        // Verify account exists first
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        
        return transactionRepository.findByAccountId(accountId);
    }
} 