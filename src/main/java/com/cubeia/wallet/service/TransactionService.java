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
    private final DoubleEntryService doubleEntryService;
    private final TransactionTemplate transactionTemplate;
    
    // Store the most recent transaction ID to support integration tests
    private UUID mostRecentTransactionId;

    /**
     * Creates a new TransactionService.
     * 
     * @param accountRepository Repository for account data access
     * @param transactionRepository Repository for transaction data access
     * @param validationService Service for validating transaction parameters
     * @param doubleEntryService Service for double-entry bookkeeping operations
     * @param transactionManager Transaction manager for managing transactions
     */
    public TransactionService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            ValidationService validationService,
            DoubleEntryService doubleEntryService,
            PlatformTransactionManager transactionManager) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.validationService = validationService;
        this.doubleEntryService = doubleEntryService;
        
        // Configure transaction template with SERIALIZABLE isolation level
        // to prevent phantom reads, non-repeatable reads, and dirty reads
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        this.transactionTemplate.setTimeout(15); // 15 seconds timeout
    }

    /**
     * Transfers funds between accounts.
     * 
     * @param fromAccountId The source account ID
     * @param toAccountId The destination account ID
     * @param amount The amount to transfer
     * @param description Optional description for the transaction
     * @return The transaction record
     * @throws AccountNotFoundException if either account is not found
     * @throws InsufficientFundsException if the source account has insufficient funds
     * @throws CurrencyMismatchException if currencies don't match
     */
    public Transaction transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String description) {
        return transfer(fromAccountId, toAccountId, amount, null, description);
    }
    
    /**
     * Transfers funds between accounts with an idempotency reference and description.
     * 
     * @param fromAccountId The source account ID
     * @param toAccountId The destination account ID
     * @param amount The amount to transfer
     * @param referenceId Optional reference ID for idempotency
     * @param description Optional description for the transaction
     * @return The transaction record
     * @throws AccountNotFoundException if either account is not found
     * @throws InsufficientFundsException if the source account has insufficient funds
     * @throws CurrencyMismatchException if currencies don't match
     */
    public Transaction transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String referenceId, String description) {
        return transactionTemplate.execute(status -> {
            try {
                // Validate parameters
                validationService.validateRequiredParameters(fromAccountId, toAccountId, amount);
                
                // Perform full validation
                TransferValidationResult validationResult = validationService.validateTransferParameters(
                    fromAccountId, toAccountId, amount, referenceId);
                
                // If we have an existing transaction, return it (idempotent operation)
                if (validationResult.existingTransaction() != null) {
                    return validationResult.existingTransaction();
                }
                
                // Get validated accounts
                Account fromAccount = validationResult.fromAccount();
                Account toAccount = validationResult.toAccount();
                
                // Get currency from the validation result
                Currency currency = fromAccount.getCurrency();
                
                // Create and save the transaction record (with description)
                Transaction transaction = new Transaction(
                    fromAccountId, 
                    toAccountId, 
                    amount, 
                    TransactionType.TRANSFER, 
                    currency, 
                    referenceId,
                    description);
                
                transaction = transactionRepository.save(transaction);
                
                // Store the most recent transaction ID for integration tests
                mostRecentTransactionId = transaction.getId();
                
                // Execute the transaction using double-entry bookkeeping
                executeTransaction(transaction);
                
                return transaction;
            } catch (Exception e) {
                // Rollback by throwing the exception
                // Spring's TransactionTemplate will handle rollback
                if (status != null) {
                    status.setRollbackOnly();
                }
                throw e;
            }
        });
    }
    
    /**
     * Executes a transaction by creating ledger entries using double-entry bookkeeping.
     * This method guarantees that balance updates are always tied to a transaction record.
     * All data for the transaction comes from the transaction object itself.
     * 
     * @param transaction The transaction record containing details of the transfer
     * @throws AccountNotFoundException if either account is not found
     * @throws InsufficientFundsException if the source account has insufficient funds
     * @throws CurrencyMismatchException if currencies don't match between accounts and transaction
     */
    protected void executeTransaction(Transaction transaction) {
        try {
            UUID fromAccountId = transaction.getFromAccountId();
            UUID toAccountId = transaction.getToAccountId();
            BigDecimal amount = transaction.getAmount();
            Currency currency = transaction.getCurrency();
            
            // Verify that the accounts exist and currencies match
            Account fromAccount = accountRepository.findById(fromAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
            Account toAccount = accountRepository.findById(toAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(toAccountId));
            
            // Verify currencies match (both accounts and transaction must have same currency)
            if (fromAccount.getCurrency() != currency || toAccount.getCurrency() != currency) {
                throw CurrencyMismatchException.forTransactionAndAccounts(
                    currency, fromAccount.getCurrency(), toAccount.getCurrency());
            }
            
            // Use the DoubleEntryService to create the ledger entries
            doubleEntryService.createTransferEntries(transaction);
            
            // Calculate the new balances from ledger entries for verification
            BigDecimal fromNewBalance = doubleEntryService.calculateBalance(fromAccountId);
            
            // Verify sufficient funds in source account
            if (fromNewBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientFundsException(fromAccount.getId(), String.format(
                    "Insufficient funds in account: %s, Current balance: %s, Required amount: %s",
                    fromAccount.getId(), doubleEntryService.calculateBalance(fromAccountId), amount));
            }
            
            // Mark transaction as successful
            transaction.markSuccess();
            transactionRepository.save(transaction);
        } catch (Exception e) {
            // Mark transaction as failed with reason
            transaction.markFailed(e.getMessage());
            transactionRepository.save(transaction);
            
            // Re-throw the exception for higher-level handling
            throw e;
        }
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
    
    /**
     * Get the current balance for an account based on ledger entries.
     * 
     * @param accountId The account ID to get the balance for
     * @return The current balance
     * @throws AccountNotFoundException if the account doesn't exist
     */
    public BigDecimal getBalance(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        
        return doubleEntryService.calculateBalance(accountId);
    }    

    protected TransactionTemplate getTransactionTemplate() {
        return this.transactionTemplate;
    }
} 