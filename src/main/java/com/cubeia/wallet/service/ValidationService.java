package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.exception.CurrencyMismatchException;
import com.cubeia.wallet.exception.InvalidTransactionException;
import com.cubeia.wallet.model.Account;
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
     * Validates transaction parameters for a transfer.
     * 
     * @param fromAccountId The ID of the account to transfer from
     * @param toAccountId The ID of the account to transfer to
     * @param amount The amount to transfer
     * @param referenceId Optional reference ID for idempotency (can be null)
     * @return A tuple of fromAccount and toAccount if validation passes
     * @throws AccountNotFoundException if either account is not found
     * @throws CurrencyMismatchException if currencies don't match
     * @throws InvalidTransactionException if a transaction with the same reference ID exists with different parameters
     */
    public TransferValidationResult validateTransferParameters(
            UUID fromAccountId, UUID toAccountId, BigDecimal amount, String referenceId) {
        
        // Idempotency check
        if (referenceId != null && !referenceId.isEmpty()) {
            boolean hasConflict = transactionRepository.findByReference(referenceId)
                .filter(existing -> 
                    !existing.getFromAccountId().equals(fromAccountId) ||
                    !existing.getToAccountId().equals(toAccountId) ||
                    existing.getAmount().compareTo(amount) != 0
                )
                .isPresent();
                
            if (hasConflict) {
                throw InvalidTransactionException.forDuplicateReference(referenceId);
            }
        }
        
        // Verify accounts exist
        Account fromAccount = accountRepository.findById(fromAccountId)
            .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        Account toAccount = accountRepository.findById(toAccountId)
            .orElseThrow(() -> new AccountNotFoundException(toAccountId));
        
        // Check for currency mismatch
        if (!fromAccount.getCurrency().equals(toAccount.getCurrency())) {
            throw CurrencyMismatchException.forTransfer(fromAccount.getCurrency(), toAccount.getCurrency());
        }
        
        return new TransferValidationResult(fromAccount, toAccount);
    }
    
    /**
     * Container for validation results.
     */
    public record TransferValidationResult(Account fromAccount, Account toAccount) {}
} 