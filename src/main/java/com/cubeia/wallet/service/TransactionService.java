package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.exception.CurrencyMismatchException;
import com.cubeia.wallet.exception.InsufficientFundsException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.TransactionRepository;
import com.cubeia.wallet.service.ValidationService.TransferValidationResult;

/**
 * Service for managing account transactions.
 */
@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ValidationService validationService;
    private final TransactionTemplate transactionTemplate;

    public TransactionService(
            AccountRepository accountRepository, 
            TransactionRepository transactionRepository,
            ValidationService validationService,
            PlatformTransactionManager transactionManager) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.validationService = validationService;
        
        // Initialize TransactionTemplate with appropriate settings
        this.transactionTemplate = createTransactionTemplate(transactionManager);
    }
    
    /**
     * Creates and configures a TransactionTemplate.
     * This method is separate to allow mocking in tests.
     */
    private TransactionTemplate createTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return template;
    }
    
    /**
     * Returns the transaction template.
     * Protected visibility to allow mocking in tests.
     */
    protected TransactionTemplate getTransactionTemplate() {
        return transactionTemplate;
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
     * @throws CurrencyMismatchException if currencies don't match
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
     * @throws CurrencyMismatchException if currencies don't match between accounts
     */
    public Transaction transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String referenceId) {
        // Consolidate all validation in ValidationService, which also handles idempotency
        TransferValidationResult validationResult = validationService.validateTransferParameters(
            fromAccountId, toAccountId, amount, referenceId);
        
        // If we found an existing transaction with same reference ID, return it for idempotency
        if (validationResult.existingTransaction() != null) {
            return validationResult.existingTransaction();
        }
        
        Account fromAccount = validationResult.fromAccount();
        Account toAccount = validationResult.toAccount();
        
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
        return getTransactionTemplate().execute(status -> {
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
     * @throws CurrencyMismatchException if currencies don't match between accounts and transaction
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
            throw CurrencyMismatchException.forTransactionAndAccounts(
                currency, fromAccount.getCurrency(), toAccount.getCurrency());
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