package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.exception.BalanceVerificationException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.EntryType;
import com.cubeia.wallet.model.LedgerEntry;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.LedgerEntryRepository;

/**
 * Core service implementing double-entry bookkeeping principles for the wallet system.
 * 
 * This service ensures financial integrity by creating balanced pairs of ledger entries
 * (debits and credits) for all transactions, calculating account balances, and verifying
 * the integrity of the double-entry system.
 * 
 * See README.md "Double-Entry Bookkeeping Implementation" section for detailed information
 * about the architecture, benefits and performance considerations of the double-entry system.
 */
@Service
public class DoubleEntryService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;

    /**
     * Creates a new DoubleEntryService instance.
     *
     * @param ledgerEntryRepository The repository for accessing and storing ledger entries
     * @param accountRepository repository for accounts
     */
    public DoubleEntryService(LedgerEntryRepository ledgerEntryRepository, AccountRepository accountRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
    }
    
    /**
     * Creates a balanced pair of ledger entries for a transfer transaction.
     * <p>
     * For each transfer, this method creates:
     * <ul>
     *   <li>A DEBIT entry on the source account</li>
     *   <li>A CREDIT entry on the destination account</li>
     * </ul>
     * </p>
     * <p>
     * This maintains the double-entry principle that for every transaction,
     * the sum of debits equals the sum of credits.
     * </p>
     *
     * @param transaction The transaction for which to create ledger entries
     * @return The transaction with updated ledger entries
     * @throws IllegalArgumentException if the transaction has invalid data
     */
    @Transactional
    public Transaction createTransferEntries(Transaction transaction) {
        // Verify accounts exist
        if (!accountRepository.existsById(transaction.getFromAccountId())) {
            throw new AccountNotFoundException(transaction.getFromAccountId());
        }
        
        if (!accountRepository.existsById(transaction.getToAccountId())) {
            throw new AccountNotFoundException(transaction.getToAccountId());
        }
        
        // Create DEBIT entry on the source account
        LedgerEntry debitEntry = LedgerEntry.builder()
                .accountId(transaction.getFromAccountId())
                .transactionId(transaction.getId())
                .entryType(EntryType.DEBIT)
                .amount(transaction.getAmount())
                .description("Transfer to account " + transaction.getToAccountId())
                .build();
        
        // Create CREDIT entry on the destination account
        LedgerEntry creditEntry = LedgerEntry.builder()
                .accountId(transaction.getToAccountId())
                .transactionId(transaction.getId())
                .entryType(EntryType.CREDIT)
                .amount(transaction.getAmount())
                .description("Transfer from account " + transaction.getFromAccountId())
                .build();
        
        // Save both entries to maintain the balanced double-entry
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);
        
        return transaction;
    }
    
    /**
     * Creates a system credit entry directly into an account without a corresponding debit.
     * <p>
     * This method is typically used for initial funding or adjustments that don't come
     * from another account within the system. In a complete implementation, this would
     * normally debit a special system account to maintain the double-entry principle.
     * </p>
     *
     * @param accountId The account to credit
     * @param amount The amount to credit (must be positive)
     * @param description The description of this credit operation
     * @throws IllegalArgumentException if parameters are invalid
     */
    @Transactional
    public void createSystemCreditEntry(UUID accountId, BigDecimal amount, String description) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        // Create a credit entry on the specified account
        LedgerEntry entry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(UUID.randomUUID()) // Generate a transaction ID since this is a system operation
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .description(description)
                .build();
        
        ledgerEntryRepository.save(entry);
    }
    
    /**
     * Calculates the current balance of an account by summing all ledger entries.
     * <p>
     * The balance is calculated as: sum(CREDIT entries) - sum(DEBIT entries).
     * This method uses SQL aggregation functions for efficiency.
     * </p>
     *
     * @param accountId The account ID for which to calculate the balance
     * @return The current balance of the account
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateBalance(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        
        return ledgerEntryRepository.calculateBalance(accountId);
    }
    
    /**
     * Verifies that an account's balance matches the expected amount.
     * <p>
     * This can be used to check for balance discrepancies or verify the 
     * results of operations.
     * </p>
     *
     * @param accountId The account ID to check
     * @param expectedBalance The expected balance
     * @return true if the calculated balance matches the expected balance
     */
    @Transactional(readOnly = true)
    public boolean verifyBalance(UUID accountId, BigDecimal expectedBalance) {
        BigDecimal actualBalance = calculateBalance(accountId);
        return actualBalance.compareTo(expectedBalance) == 0;
    }
    
    /**
     * Verifies that an account's balance calculated from ledger entries matches the stored balance in the Account entity.
     * <p>
     * This is useful for transition and migration validation.
     * </p>
     *
     * @param account the account to verify
     * @throws BalanceVerificationException if the calculated balance doesn't match the stored balance
     */
    @Transactional(readOnly = true)
    public void verifyAccountBalance(Account account) {
        BigDecimal calculatedBalance = calculateBalance(account.getId());
        
        if (calculatedBalance.compareTo(account.getBalance()) != 0) {
            throw new BalanceVerificationException(account.getId(), account.getBalance(), calculatedBalance);
        }
    }
    
    /**
     * Gets all ledger entries for a specific account.
     *
     * @param accountId the ID of the account
     * @return list of ledger entries
     * @throws AccountNotFoundException if the account doesn't exist
     */
    @Transactional(readOnly = true)
    public List<LedgerEntry> getAccountEntries(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        
        return ledgerEntryRepository.findByAccountIdOrderByTimestampDesc(accountId);
    }
    
    /**
     * Gets all ledger entries related to a specific transaction.
     *
     * @param transactionId the ID of the transaction
     * @return list of ledger entries
     */
    @Transactional(readOnly = true)
    public List<LedgerEntry> getTransactionEntries(UUID transactionId) {
        return ledgerEntryRepository.findByTransactionId(transactionId);
    }
} 