package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.exception.BalanceVerificationException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
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

    // ID for the system funding account - source of all system credits
    private static final UUID SYSTEM_FUNDING_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

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
        
        // Ensure the system funding account exists
        ensureSystemFundingAccount();
    }
    
    /**
     * Ensures the system funding account exists in the database.
     * This account is the source of all system credits to maintain double-entry integrity.
     */
    @Transactional
    private void ensureSystemFundingAccount() {
        try {
            accountRepository.findById(SYSTEM_FUNDING_ACCOUNT_ID)
                .orElseGet(() -> {
                    // Create the system account with explicit ID
                    Account systemAccount = new Account(Currency.EUR, AccountType.SystemAccount.getInstance());
                    
                    try {
                        // Use reflection to set the ID since it's normally generated
                        java.lang.reflect.Field idField = Account.class.getDeclaredField("id");
                        idField.setAccessible(true);
                        idField.set(systemAccount, SYSTEM_FUNDING_ACCOUNT_ID);
                        
                        // Save the account, ignoring concurrent creation errors
                        try {
                            return accountRepository.save(systemAccount);
                        } catch (Exception e) {
                            // Log warning but continue - another thread may have created it
                            System.err.println("Note: System funding account may have been created by another thread: " + e.getMessage());
                            // Return the account anyway as best effort
                            return systemAccount;
                        }
                    } catch (Exception ex) {
                        // Log warning but continue
                        System.err.println("Failed to create system funding account via reflection: " + ex.getMessage());
                        return systemAccount;
                    }
                });
        } catch (Exception e) {
            // Log warning but continue - the application can still function
            System.err.println("Failed to create system funding account: " + e.getMessage());
        }
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
     * <p>
     * The method also checks for existing entries to ensure idempotency.
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
        
        // Check if entries already exist for this transaction (idempotency)
        List<LedgerEntry> existingEntries = ledgerEntryRepository.findByTransactionId(transaction.getId());
        if (!existingEntries.isEmpty()) {
            // Entries already exist, just return the transaction
            return transaction;
        }
        
        // Create DEBIT entry on the source account
        LedgerEntry debitEntry = LedgerEntry.builder()
                .accountId(transaction.getFromAccountId())
                .transactionId(transaction.getId())
                .entryType(EntryType.DEBIT)
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .description("Transfer to account " + transaction.getToAccountId())
                .build();
        
        // Create CREDIT entry on the destination account
        LedgerEntry creditEntry = LedgerEntry.builder()
                .accountId(transaction.getToAccountId())
                .transactionId(transaction.getId())
                .entryType(EntryType.CREDIT)
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .description("Transfer from account " + transaction.getFromAccountId())
                .build();
        
        // Save both entries to maintain the balanced double-entry
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);
        
        return transaction;
    }
    
    /**
     * Creates a system credit entry with a corresponding debit from the system account.
     * <p>
     * This method maintains proper double-entry bookkeeping by debiting a special
     * system account for all system credits.
     * </p>
     *
     * @param accountId The account to credit
     * @param amount The amount to credit (must be positive)
     * @param description The description of this credit operation
     * @param currency The currency of the credit operation
     * @throws IllegalArgumentException if parameters are invalid
     */
    @Transactional
    public void createSystemCreditEntry(UUID accountId, BigDecimal amount, String description, Currency currency) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
        
        // Ensure the target account exists
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        
        // Generate a transaction ID for this system operation
        UUID transactionId = UUID.randomUUID();
        
        // Check if entries already exist for this transaction (idempotency)
        List<LedgerEntry> existingEntries = ledgerEntryRepository.findByTransactionId(transactionId);
        if (!existingEntries.isEmpty()) {
            // Entries already exist, just return
            return;
        }
        
        // Create a CREDIT entry on the specified account
        LedgerEntry creditEntry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .currency(currency)
                .description(description)
                .build();
        
        // Create a DEBIT entry on the system funding account to maintain double-entry integrity
        LedgerEntry debitEntry = LedgerEntry.builder()
                .accountId(SYSTEM_FUNDING_ACCOUNT_ID)
                .transactionId(transactionId)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .currency(currency)
                .description("System funding: " + description + " for account " + accountId)
                .build();
        
        // Save both entries to maintain double-entry integrity
        ledgerEntryRepository.save(creditEntry);
        ledgerEntryRepository.save(debitEntry);
    }
    
    /**
     * Creates a system credit entry with a corresponding debit from the system account.
     * This overload assumes EUR as the default currency for backward compatibility.
     *
     * @param accountId The account to credit
     * @param amount The amount to credit (must be positive)
     * @param description The description of this credit operation
     * @throws IllegalArgumentException if parameters are invalid
     */
    @Transactional
    public void createSystemCreditEntry(UUID accountId, BigDecimal amount, String description) {
        createSystemCreditEntry(accountId, amount, description, Currency.EUR);
    }
    
    /**
     * Calculates the current balance of an account by summing all ledger entries across all currencies.
     * <p>
     * The balance is calculated as: sum(CREDIT entries) - sum(DEBIT entries).
     * This method uses SQL aggregation functions for efficiency.
     * </p>
     *
     * @param accountId The account ID for which to calculate the balance
     * @return The current balance of the account combining all currencies
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateBalance(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        
        return ledgerEntryRepository.calculateBalance(accountId);
    }
    
    /**
     * Verifies that an account's balance matches the expected amount combining all currencies.
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
        BigDecimal calculatedBalance = calculateBalanceByCurrency(account.getId(), account.getCurrency());
        
        if (calculatedBalance.compareTo(account.getBalance()) != 0) {
            throw new BalanceVerificationException(account.getId(), account.getBalance(), calculatedBalance);
        }
    }
    
    /**
     * Gets all ledger entries for a specific account across all currencies.
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
    
    /**
     * Calculates the current balance of an account by summing all ledger entries for a specific currency.
     * <p>
     * The balance is calculated as: sum(CREDIT entries) - sum(DEBIT entries).
     * This method uses SQL aggregation functions for efficiency.
     * </p>
     *
     * @param accountId The account ID for which to calculate the balance
     * @param currency The currency to calculate the balance for
     * @return The current balance of the account for the specified currency
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateBalanceByCurrency(UUID accountId, Currency currency) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
        
        return ledgerEntryRepository.calculateBalanceByCurrency(accountId, currency);
    }
    
    /**
     * Verifies that an account's balance matches the expected amount for a specific currency.
     * <p>
     * This can be used to check for balance discrepancies or verify the 
     * results of operations.
     * </p>
     *
     * @param accountId The account ID to check
     * @param expectedBalance The expected balance
     * @param currency The currency to check
     * @return true if the calculated balance matches the expected balance
     */
    @Transactional(readOnly = true)
    public boolean verifyBalanceByCurrency(UUID accountId, BigDecimal expectedBalance, Currency currency) {
        BigDecimal actualBalance = calculateBalanceByCurrency(accountId, currency);
        return actualBalance.compareTo(expectedBalance) == 0;
    }
    
    /**
     * Gets all ledger entries for a specific account and currency.
     *
     * @param accountId the ID of the account
     * @param currency the currency to filter by
     * @return list of ledger entries
     * @throws AccountNotFoundException if the account doesn't exist
     */
    @Transactional(readOnly = true)
    public List<LedgerEntry> getAccountEntriesByCurrency(UUID accountId, Currency currency) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        
        return ledgerEntryRepository.findByAccountIdAndCurrencyOrderByTimestampDesc(accountId, currency);
    }
} 