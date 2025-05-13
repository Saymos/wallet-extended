package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.util.List;
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
     * Transfers funds between accounts using a deadlock prevention strategy.
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
        // Always acquire locks in the same order to prevent deadlocks
        Account fromAccount;
        Account toAccount;
        boolean isReversedLockOrder = false;
        
        if (fromAccountId.compareTo(toAccountId) <= 0) {
            // Regular order: fromAccount has lower or equal ID
            fromAccount = accountRepository.findByIdWithLock(fromAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
            toAccount = accountRepository.findByIdWithLock(toAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(toAccountId));
        } else {
            // Reversed order: toAccount has lower ID
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
            currency
        );
        
        // Execute the transaction (update balances)
        try {
            transaction.execute(transaction, fromAccount, toAccount);
        } catch (IllegalArgumentException e) {
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