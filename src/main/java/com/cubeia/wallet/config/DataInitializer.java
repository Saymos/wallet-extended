package com.cubeia.wallet.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.EntryType;
import com.cubeia.wallet.model.LedgerEntry;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.LedgerEntryRepository;

/**
 * Initializes test account data when the application starts.
 * Creates a test account with funds for transaction testing.
 */
@Component
@Order(1) // Run early in the initialization process
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final BigDecimal INITIAL_FUNDS = new BigDecimal("1000.00");
    
    // A fixed UUID for the test account to make it easily identifiable
    private static final UUID TEST_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    
    // System account ID (matching the one in DoubleEntryService)
    private static final UUID SYSTEM_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public DataInitializer(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting data initialization for test accounts...");
        try {
            // Create or verify system account exists
            createOrUpdateSystemAccount();
            
            // Create or verify test account exists
            createOrUpdateTestAccount();
            
            // Add funds to test account if needed
            addFundsToTestAccount();
            
            log.info("Data initialization completed successfully!");
        } catch (Exception e) {
            log.error("Error initializing test accounts: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to set the ID field of an Account using reflection.
     * This allows us to set a predefined UUID without adding a specific constructor.
     * 
     * @param account The account to set the ID on
     * @param id The UUID to set as the account's ID
     * @throws RuntimeException if reflection fails
     */
    private void setAccountId(Account account, UUID id) {
        try {
            java.lang.reflect.Field idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(account, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set account ID via reflection", e);
        }
    }
    
    private void createOrUpdateSystemAccount() {
        try {
            boolean exists = accountRepository.existsById(SYSTEM_ACCOUNT_ID);
            log.info("System account existence check: {}", exists);
            
            if (!exists) {
                log.info("Creating system account with ID: {}", SYSTEM_ACCOUNT_ID);
                
                // Create account without ID in constructor
                Account systemAccount = new Account(Currency.EUR, AccountType.SystemAccount.getInstance());
                // Set ID using reflection
                setAccountId(systemAccount, SYSTEM_ACCOUNT_ID);
                
                Account saved = accountRepository.save(systemAccount);
                
                log.info("System account created successfully: {}", saved.getId());
            } else {
                log.info("System account already exists with ID: {}", SYSTEM_ACCOUNT_ID);
            }
        } catch (Exception e) {
            log.error("Error creating system account: {}", e.getMessage(), e);
            throw e; // Rethrow to fail initialization
        }
    }
    
    private void createOrUpdateTestAccount() {
        try {
            boolean exists = accountRepository.existsById(TEST_ACCOUNT_ID);
            log.info("Test account existence check: {}", exists);
            
            if (!exists) {
                log.info("Creating test account with ID: {}", TEST_ACCOUNT_ID);
                
                // Create account without ID in constructor
                Account testAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
                // Set ID using reflection
                setAccountId(testAccount, TEST_ACCOUNT_ID);
                
                Account saved = accountRepository.save(testAccount);
                
                log.info("Test account created successfully: {}", saved.getId());
            } else {
                log.info("Test account already exists with ID: {}", TEST_ACCOUNT_ID);
            }
        } catch (Exception e) {
            log.error("Error creating test account: {}", e.getMessage(), e);
            throw e; // Rethrow to fail initialization
        }
    }
    
    private void addFundsToTestAccount() {
        try {
            // Check if the account already has funds
            List<LedgerEntry> existingEntries = ledgerEntryRepository.findByAccountIdOrderByTimestampDesc(TEST_ACCOUNT_ID);
            log.info("Test account existing entries count: {}", existingEntries.size());
            
            if (existingEntries.isEmpty()) {
                log.info("Adding initial funds to test account");
                
                // Create a transaction ID for this funding operation
                UUID transactionId = UUID.randomUUID();
                log.info("Generated transaction ID for funding: {}", transactionId);
                
                // Create credit entry for the test account
                LedgerEntry creditEntry = new LedgerEntry.Builder()
                    .accountId(TEST_ACCOUNT_ID)
                    .transactionId(transactionId)
                    .amount(INITIAL_FUNDS)
                    .currency(Currency.EUR)
                    .entryType(EntryType.CREDIT)
                    .description("Initial funding for test account")
                    .build();
                
                // Create corresponding debit entry from system account
                LedgerEntry debitEntry = new LedgerEntry.Builder()
                    .accountId(SYSTEM_ACCOUNT_ID)
                    .transactionId(transactionId)
                    .amount(INITIAL_FUNDS)
                    .currency(Currency.EUR)
                    .entryType(EntryType.DEBIT)
                    .description("System funding for test account")
                    .build();
                
                // Save both entries
                LedgerEntry savedCredit = ledgerEntryRepository.save(creditEntry);
                LedgerEntry savedDebit = ledgerEntryRepository.save(debitEntry);
                
                log.info("Added entry to test account, ID: {}", savedCredit.getId());
                log.info("Added corresponding entry to system account, ID: {}", savedDebit.getId());
                log.info("Added {} EUR to test account", INITIAL_FUNDS);
            } else {
                log.info("Test account already has funds - existing entries: {}", existingEntries.size());
                for (LedgerEntry entry : existingEntries.subList(0, Math.min(existingEntries.size(), 3))) {
                    log.info("Entry: {} {} {}", entry.getId(), entry.getEntryType(), entry.getAmount());
                }
            }
        } catch (Exception e) {
            log.error("Error adding funds to test account: {}", e.getMessage(), e);
            throw e; // Rethrow to fail initialization
        }
    }
    
} 