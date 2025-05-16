package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.cubeia.wallet.WalletApplication;
import com.cubeia.wallet.exception.CurrencyMismatchException;
import com.cubeia.wallet.exception.InsufficientFundsException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.TransactionRepository;

/**
 * Integration tests for TransactionService that tests
 * edge cases involving InsufficientFundsException handling.
 */
@SpringBootTest(classes = WalletApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TransactionServiceIntegrationTest {
    
    private static final Logger log = LoggerFactory.getLogger(TransactionServiceIntegrationTest.class);
    
    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private TransactionTemplate transactionTemplate;
    
    @Autowired
    private DoubleEntryService doubleEntryService;
    
    /**
     * Helper method to create a system credit for an account
     */
    private void createSystemCredit(UUID accountId, BigDecimal amount) {
        transactionTemplate.execute(status -> {
            doubleEntryService.createSystemCreditEntry(accountId, amount, "System credit for testing");
            BigDecimal balance = doubleEntryService.calculateBalance(accountId);
            log.info("Created system credit of {} for account {}. New balance: {}", amount, accountId, balance);
            return balance;
        });
    }
    
    /**
     * Helper method to create and prepare an account
     */
    private Account createAndPrepareAccount(Currency currency, AccountType accountType) {
        Account account = new Account(currency, accountType);
        account = accountRepository.save(account);
        return account;
    }
    
    @Test
    @Transactional
    void testInsufficientFundsExceptionHandling() {
        // Create accounts
        final Account fromAccount = createAndPrepareAccount(Currency.EUR, AccountType.MainAccount.getInstance());
        createSystemCredit(fromAccount.getId(), new BigDecimal("50.00")); // Only 50 EUR
        
        final Account toAccount = createAndPrepareAccount(Currency.EUR, AccountType.MainAccount.getInstance());
        
        BigDecimal transferAmount = new BigDecimal("100.00"); // More than available
        
        // Verify the exception is thrown and contains the right message
        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class, () -> {
            transactionService.transfer(fromAccount.getId(), toAccount.getId(), transferAmount, null, null);
        });
        
        assertTrue(exception.getMessage().contains("Insufficient funds"));
        assertTrue(exception.getMessage().contains(fromAccount.getId().toString()));
    }
    
    @Test
    @Transactional
    void testOtherIllegalArgumentException() {
        // Create accounts
        final Account fromAccount = createAndPrepareAccount(Currency.EUR, AccountType.MainAccount.getInstance());
        createSystemCredit(fromAccount.getId(), new BigDecimal("100.00"));
        
        final Account toAccount = createAndPrepareAccount(Currency.USD, AccountType.MainAccount.getInstance()); // Different currency
        
        BigDecimal transferAmount = new BigDecimal("50.00");
        
        // Attempt to transfer between accounts with different currencies
        CurrencyMismatchException exception = assertThrows(CurrencyMismatchException.class, () -> {
            transactionService.transfer(fromAccount.getId(), toAccount.getId(), transferAmount, null, null);
        });
        
        assertTrue(exception.getMessage().contains("different currencies"));
    }
    
    @Test
    void testTransactionTemplateIsolation() {
        // Execute in a transaction to ensure all setup operations are committed
        final Account[] accounts = transactionTemplate.execute(status -> {
            Account from = createAndPrepareAccount(Currency.EUR, AccountType.MainAccount.getInstance());
            Account to = createAndPrepareAccount(Currency.EUR, AccountType.MainAccount.getInstance());
            
            // Create system credits to fund the accounts within the transaction
            doubleEntryService.createSystemCreditEntry(from.getId(), new BigDecimal("500.00"), "System credit for testing");
            doubleEntryService.createSystemCreditEntry(to.getId(), new BigDecimal("50.00"), "System credit for testing");
            
            // Verify initial balances to ensure credits were applied
            BigDecimal initialFromBalance = doubleEntryService.calculateBalance(from.getId());
            BigDecimal initialToBalance = doubleEntryService.calculateBalance(to.getId());
            
            log.info("Source account {} funded with balance {}", from.getId(), initialFromBalance);
            log.info("Destination account {} funded with balance {}", to.getId(), initialToBalance);
            
            assertEquals(0, new BigDecimal("500.00").compareTo(initialFromBalance),
                    "Source account should start with 500.00");
            assertEquals(0, new BigDecimal("50.00").compareTo(initialToBalance),
                    "Destination account should start with 50.00");
            
            return new Account[] { from, to };
        });
        
        // Capture the account IDs for use outside the transaction
        final Account fromAccount = accounts[0];
        final Account toAccount = accounts[1];
        
        final UUID fromAccountId = fromAccount.getId();
        final UUID toAccountId = toAccount.getId();
        
        BigDecimal transferAmount = new BigDecimal("75.00");
        
        // Execute transfer and verify it completes atomically
        Transaction transaction = transactionService.transfer(
                fromAccountId, toAccountId, transferAmount, null, null);
        
        // Verify the transaction details
        assertNotNull(transaction);
        assertNotNull(transaction.getId());
        assertEquals(fromAccountId, transaction.getFromAccountId());
        assertEquals(toAccountId, transaction.getToAccountId());
        assertEquals(transferAmount, transaction.getAmount());
        
        // Verify account balances
        assertEquals(0, new BigDecimal("425.00").compareTo(doubleEntryService.calculateBalance(fromAccountId)),
                "Source account balance should be 425.00");
        assertEquals(0, new BigDecimal("125.00").compareTo(doubleEntryService.calculateBalance(toAccountId)),
                "Destination account balance should be 125.00");
    }
    
    @Test
    void testIdempotencyWithTransactionTemplate() {
        // Execute in a transaction to ensure all setup operations are committed
        final Account[] accounts = transactionTemplate.execute(status -> {
            Account from = createAndPrepareAccount(Currency.EUR, AccountType.MainAccount.getInstance());
            Account to = createAndPrepareAccount(Currency.EUR, AccountType.MainAccount.getInstance());
            
            // Create system credits to fund the accounts within the transaction
            doubleEntryService.createSystemCreditEntry(from.getId(), new BigDecimal("500.00"), "System credit for testing");
            doubleEntryService.createSystemCreditEntry(to.getId(), new BigDecimal("50.00"), "System credit for testing");
            
            // Verify initial balances to ensure credits were applied
            BigDecimal initialFromBalance = doubleEntryService.calculateBalance(from.getId());
            BigDecimal initialToBalance = doubleEntryService.calculateBalance(to.getId());
            
            log.info("Source account {} funded with balance {}", from.getId(), initialFromBalance);
            log.info("Destination account {} funded with balance {}", to.getId(), initialToBalance);
            
            assertEquals(0, new BigDecimal("500.00").compareTo(initialFromBalance),
                    "Source account should start with 500.00");
            assertEquals(0, new BigDecimal("50.00").compareTo(initialToBalance),
                    "Destination account should start with 50.00");
            
            return new Account[] { from, to };
        });
        
        // Capture the account IDs for use outside the transaction
        final Account fromAccount = accounts[0];
        final Account toAccount = accounts[1];
        
        final UUID fromAccountId = fromAccount.getId();
        final UUID toAccountId = toAccount.getId();
        
        BigDecimal transferAmount = new BigDecimal("75.00");
        String referenceId = "IDEMPOTENCY-TEST-" + UUID.randomUUID();
        
        // Execute first transfer
        Transaction firstTransaction = transactionService.transfer(
                fromAccountId, toAccountId, transferAmount, referenceId, null);
        
        // Get balances after first transfer
        BigDecimal fromBalanceAfterFirst = doubleEntryService.calculateBalance(fromAccountId);
        BigDecimal toBalanceAfterFirst = doubleEntryService.calculateBalance(toAccountId);
        
        // Execute second transfer with same reference ID
        Transaction secondTransaction = transactionService.transfer(
                fromAccountId, toAccountId, transferAmount, referenceId, null);
        
        // Verify it's the same transaction
        assertEquals(firstTransaction.getId(), secondTransaction.getId());
        
        // Get balances after second transfer attempt
        BigDecimal fromBalanceAfterSecond = doubleEntryService.calculateBalance(fromAccountId);
        BigDecimal toBalanceAfterSecond = doubleEntryService.calculateBalance(toAccountId);
        
        // Verify account balances haven't changed after second attempt
        assertEquals(fromBalanceAfterFirst, fromBalanceAfterSecond);
        assertEquals(toBalanceAfterFirst, toBalanceAfterSecond);
        
        // Verify final balances
        assertEquals(0, new BigDecimal("425.00").compareTo(fromBalanceAfterSecond),
                "Source account balance should be 425.00");
        assertEquals(0, new BigDecimal("125.00").compareTo(toBalanceAfterSecond),
                "Destination account balance should be 125.00");
    }
} 