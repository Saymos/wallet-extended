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
 * Service for managing double-entry bookkeeping operations.
 * <p>
 * This service implements the core functionality for a double-entry bookkeeping system,
 * where each financial transaction generates at least two ledger entries (one DEBIT and one CREDIT)
 * and the sum of all debits equals the sum of all credits.
 * </p>
 */
@Service
public class DoubleEntryService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;

    /**
     * Constructs a new DoubleEntryService with the required repositories.
     *
     * @param ledgerEntryRepository repository for ledger entries
     * @param accountRepository repository for accounts
     */
    public DoubleEntryService(LedgerEntryRepository ledgerEntryRepository, AccountRepository accountRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
    }
    
    /**
     * Creates the balanced ledger entries for a transaction.
     * <p>
     * For a standard transfer transaction, this will create:
     * - A DEBIT entry for the source account (fromAccount)
     * - A CREDIT entry for the destination account (toAccount)
     * </p>
     *
     * @param transaction the transaction to create entries for
     * @return the list of created ledger entries
     * @throws AccountNotFoundException if any of the accounts doesn't exist
     */
    @Transactional
    public List<LedgerEntry> createTransferEntries(Transaction transaction) {
        // Verify accounts exist
        if (!accountRepository.existsById(transaction.getFromAccountId())) {
            throw new AccountNotFoundException(transaction.getFromAccountId());
        }
        
        if (!accountRepository.existsById(transaction.getToAccountId())) {
            throw new AccountNotFoundException(transaction.getToAccountId());
        }
        
        // Create debit entry for source account
        LedgerEntry debitEntry = LedgerEntry.builder()
                .accountId(transaction.getFromAccountId())
                .transactionId(transaction.getId())
                .entryType(EntryType.DEBIT)
                .amount(transaction.getAmount())
                .description("Transfer to account " + transaction.getToAccountId())
                .build();
        
        // Create credit entry for destination account
        LedgerEntry creditEntry = LedgerEntry.builder()
                .accountId(transaction.getToAccountId())
                .transactionId(transaction.getId())
                .entryType(EntryType.CREDIT)
                .amount(transaction.getAmount())
                .description("Transfer from account " + transaction.getFromAccountId())
                .build();
        
        // Save and return both entries
        return ledgerEntryRepository.saveAll(List.of(debitEntry, creditEntry));
    }
    
    /**
     * Creates a direct credit entry for an account.
     * <p>
     * IMPORTANT: This method should only be used for system operations or testing,
     * as it creates an unbalanced entry which violates double-entry bookkeeping principles.
     * In a real double-entry system, every credit must have a corresponding debit.
     * </p>
     *
     * @param accountId the ID of the account to credit
     * @param amount the amount to credit
     * @param description a description of the operation
     * @return the created ledger entry
     * @throws AccountNotFoundException if the account doesn't exist
     */
    @Transactional
    public LedgerEntry createSystemCreditEntry(UUID accountId, BigDecimal amount, String description) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        
        // Create a system credit entry
        LedgerEntry creditEntry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(UUID.randomUUID())  // Generate a dummy transaction ID
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .description(description)
                .build();
        
        // Save and return the entry
        return ledgerEntryRepository.save(creditEntry);
    }
    
    /**
     * Calculates the current balance for an account based on its ledger entries.
     * <p>
     * The balance is calculated as the sum of all CREDIT entries minus the sum of all DEBIT entries.
     * </p>
     *
     * @param accountId the ID of the account to calculate balance for
     * @return the current balance
     * @throws AccountNotFoundException if the account doesn't exist
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateBalance(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        
        return ledgerEntryRepository.calculateBalance(accountId);
    }
    
    /**
     * Verifies that an account's balance matches the expected value.
     * <p>
     * This is useful for integrity checks and reconciliation.
     * </p>
     *
     * @param accountId the ID of the account to verify
     * @param expectedBalance the expected balance value
     * @throws AccountNotFoundException if the account doesn't exist
     * @throws BalanceVerificationException if the actual balance doesn't match the expected balance
     */
    @Transactional(readOnly = true)
    public void verifyBalance(UUID accountId, BigDecimal expectedBalance) {
        BigDecimal actualBalance = calculateBalance(accountId);
        
        if (actualBalance.compareTo(expectedBalance) != 0) {
            throw new BalanceVerificationException(accountId, expectedBalance, actualBalance);
        }
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