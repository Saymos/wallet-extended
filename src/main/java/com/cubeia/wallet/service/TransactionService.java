package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.util.List;

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
 * Service for handling transactions between accounts.
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
     * Transfers funds from one account to another.
     *
     * @param fromAccountId the ID of the sender account
     * @param toAccountId the ID of the receiver account
     * @param amount the amount to transfer
     * @return the created transaction
     * @throws AccountNotFoundException if either account is not found
     * @throws InsufficientFundsException if the sender has insufficient funds
     * @throws IllegalArgumentException if the amount is not positive
     */
    @Transactional
    public Transaction transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        // To prevent deadlocks, always acquire locks in a consistent order based on account ID
        Account firstAccount;
        Account secondAccount;
        boolean isReversedLockOrder = false;

        // Determine which account ID is lower and should be locked first
        if (fromAccountId.compareTo(toAccountId) <= 0) {
            // Regular order: fromAccount has lower or equal ID
            firstAccount = accountRepository.findByIdWithLock(fromAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
            secondAccount = accountRepository.findByIdWithLock(toAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(toAccountId));
        } else {
            // Reversed order: toAccount has lower ID
            isReversedLockOrder = true;
            firstAccount = accountRepository.findByIdWithLock(toAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(toAccountId));
            secondAccount = accountRepository.findByIdWithLock(fromAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        }

        // Now map to fromAccount and toAccount based on the actual order we used
        Account fromAccount = isReversedLockOrder ? secondAccount : firstAccount;
        Account toAccount = isReversedLockOrder ? firstAccount : secondAccount;
        
        // Check for currency match
        if (fromAccount.getCurrency() != toAccount.getCurrency()) {
            throw new IllegalArgumentException("""
                Currency mismatch: Cannot transfer between accounts with different currencies
                From account currency: %s
                To account currency: %s
                """.formatted(fromAccount.getCurrency(), toAccount.getCurrency()));
        }

        // Check withdrawal limits based on account type using the new method
        if (amount.compareTo(fromAccount.getMaxWithdrawalAmount()) > 0) {
            throw new InsufficientFundsException(fromAccountId, 
                "Exceeds withdrawal limit for account type: " + fromAccount.getAccountType().name());
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
        } catch (Exception e) {
            // Using pattern matching to handle exceptions
            if (e instanceof IllegalArgumentException ex && ex.getMessage().contains("Insufficient funds")) {
                throw new InsufficientFundsException(fromAccountId, amount.toString());
            }
            throw e;
        }

        // Save updated accounts
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // Save and return the transaction record
        return transactionRepository.save(transaction);
    }

    /**
     * Gets all transactions for an account (either as sender or receiver).
     *
     * @param accountId the account ID
     * @return list of transactions involving the account
     */
    public List<Transaction> getTransactionsByAccountId(Long accountId) {
        return transactionRepository.findByAccountId(accountId);
    }
} 