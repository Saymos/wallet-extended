package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.exception.CurrencyMismatchException;
import com.cubeia.wallet.exception.InsufficientFundsException;
import com.cubeia.wallet.exception.InvalidTransactionException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.TransactionRepository;

/**
 * Service responsible for validating transaction parameters.
 * Centralizes validation logic to avoid duplication between controller and service layers.
 */
@Service
public class ValidationService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public ValidationService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Validates all transaction parameters for a transfer.
     * Performs checks in a logical order to fail fast on any validation error.
     * 
     * @param fromAccountId The ID of the account to transfer from
     * @param toAccountId The ID of the account to transfer to
     * @param amount The amount to transfer
     * @param referenceId Optional reference ID for idempotency (can be null)
     * @return A tuple of fromAccount and toAccount if validation passes, along with existing transaction if found
     * @throws AccountNotFoundException if either account is not found
     * @throws CurrencyMismatchException if currencies don't match
     * @throws InvalidTransactionException if a transaction with the same reference ID exists with different parameters
     * @throws InsufficientFundsException if the from account has insufficient funds
     */
    public TransferValidationResult validateTransferParameters(
            UUID fromAccountId, UUID toAccountId, BigDecimal amount, String referenceId) {
        
        // Step 1: Validate idempotency first (if reference ID is provided)
        Transaction existingTransaction = null;
        if (referenceId != null && !referenceId.isEmpty()) {
            existingTransaction = transactionRepository.findByReference(referenceId).orElse(null);
            
            if (existingTransaction != null) {
                // If there's an existing transaction, validate it matches the current parameters
                validateExistingTransactionMatch(existingTransaction, fromAccountId, toAccountId, amount);
            }
        }
        
        // Step 2: Verify accounts exist
        Account fromAccount = accountRepository.findById(fromAccountId)
            .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        Account toAccount = accountRepository.findById(toAccountId)
            .orElseThrow(() -> new AccountNotFoundException(toAccountId));
        
        // Step 3: Check for currency mismatch
        if (!fromAccount.getCurrency().equals(toAccount.getCurrency())) {
            throw CurrencyMismatchException.forTransfer(fromAccount.getCurrency(), toAccount.getCurrency());
        }
        
        // Step 4: Validate amount and sufficient funds
        validateAmount(amount);
        validateSufficientFunds(fromAccount, amount);
        
        return new TransferValidationResult(fromAccount, toAccount, existingTransaction);
    }
    
    /**
     * Validates that the amount is positive.
     * 
     * @param amount The amount to validate
     * @throws InvalidTransactionException if the amount is not positive
     */
    public void validateAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw InvalidTransactionException.forNonPositiveAmount();
        }
    }
    
    /**
     * Validates that an account has sufficient funds for a withdrawal.
     * Takes into account the account type which may restrict withdrawals.
     * 
     * @param account The account to check
     * @param amount The amount to withdraw
     * @throws InsufficientFundsException if the account has insufficient funds
     */
    public void validateSufficientFunds(Account account, BigDecimal amount) {
        BigDecimal maxWithdrawal = account.getMaxWithdrawalAmount();
        
        if (amount.compareTo(maxWithdrawal) > 0) {
            String reason = String.format(
                "Amount %s exceeds maximum withdrawal amount %s for account type %s",
                amount, maxWithdrawal, account.getAccountType());
            throw new InsufficientFundsException(account.getId(), reason);
        }
    }
    
    /**
     * Validates that an existing transaction matches the current transaction parameters.
     * Used for idempotency checks to ensure repeating a transaction with the same reference
     * has the same parameters.
     * 
     * @param existingTransaction The existing transaction with the same reference ID
     * @param fromAccountId The source account ID to check
     * @param toAccountId The destination account ID to check
     * @param amount The amount to check
     * @throws InvalidTransactionException if any parameter doesn't match
     */
    public void validateExistingTransactionMatch(
            Transaction existingTransaction, UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
        
        if (!existingTransaction.getFromAccountId().equals(fromAccountId) ||
            !existingTransaction.getToAccountId().equals(toAccountId) ||
            existingTransaction.getAmount().compareTo(amount) != 0) {
            
            throw InvalidTransactionException.forDuplicateReference(existingTransaction.getReference());
        }
    }
    
    /**
     * Container for validation results.
     * Includes the source and destination accounts and any existing transaction
     * found during idempotency checks.
     */
    public record TransferValidationResult(
            Account fromAccount, 
            Account toAccount, 
            Transaction existingTransaction) {
        
        /**
         * Constructor that defaults existingTransaction to null.
         * Provided for backward compatibility.
         */
        public TransferValidationResult(Account fromAccount, Account toAccount) {
            this(fromAccount, toAccount, null);
        }
    }
} 