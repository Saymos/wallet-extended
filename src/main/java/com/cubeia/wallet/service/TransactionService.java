package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public TransactionService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
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
     */
    @Transactional
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
     * @throws IllegalArgumentException if a transaction with the same reference ID exists with different parameters
     */
    @Transactional
    public Transaction transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String referenceId) {
        // Check for existing transaction with the same reference ID
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
        
        // Always acquire locks in the same order to prevent deadlocks
        Account fromAccount;
        Account toAccount;
        boolean isReversedLockOrder = false;
        
        if (fromAccountId.compareTo(toAccountId) <= 0) {
            // Regular order: fromAccount has lower or equal ID
            // This is the normal case where we lock the source account first
            fromAccount = accountRepository.findByIdWithLock(fromAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
            toAccount = accountRepository.findByIdWithLock(toAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(toAccountId));
        } else {
            // Reversed order: toAccount has lower ID
            // To prevent deadlocks, we lock the account with the lower ID first,
            // even though it's the destination account in this case
            isReversedLockOrder = true;
            toAccount = accountRepository.findByIdWithLock(toAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(toAccountId));
            fromAccount = accountRepository.findByIdWithLock(fromAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        }
        
        // Check for currency mismatch
        if (!fromAccount.getCurrency().equals(toAccount.getCurrency())) {
            throw new IllegalArgumentException(String.format(
                "Currency mismatch: Cannot transfer between accounts with different currencies (%s and %s)",
                fromAccount.getCurrency(), toAccount.getCurrency()));
        }
        
        // Check for sufficient funds
        BigDecimal maxWithdrawal = fromAccount.getMaxWithdrawalAmount();
        if (amount.compareTo(maxWithdrawal) > 0) {
            String reason = String.format(
                "Amount %s exceeds maximum withdrawal amount %s for account type %s",
                amount, maxWithdrawal, fromAccount.getAccountType());
            throw new InsufficientFundsException(fromAccountId, reason);
        }
        
        // Create the transaction object
        Currency currency = fromAccount.getCurrency();
        Transaction transaction = new Transaction(
            fromAccountId, 
            toAccountId, 
            amount, 
            TransactionType.TRANSFER, 
            currency,
            referenceId
        );
        
        // Execute the transaction (update balances)
        try {
            transaction.execute(transaction, fromAccount, toAccount);
        } catch (IllegalArgumentException e) {
            // Defensive code: This exception handler exists for robustness but should rarely if ever be triggered
            // in normal operation since:
            // 1. We've already checked the balance with getMaxWithdrawalAmount()
            // 2. The account is locked with PESSIMISTIC_WRITE, preventing concurrent modifications
            // 3. The entire operation is within a @Transactional boundary
            // However, if somehow an IllegalArgumentException with "Insufficient funds" occurs, 
            // we convert it to our domain-specific exception
            if (e.getMessage().contains("Insufficient funds")) {
                throw new InsufficientFundsException(fromAccountId, e.getMessage());
            }
            throw e;
        }
        
        // Save updated accounts and transaction
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
        return transactionRepository.save(transaction);
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