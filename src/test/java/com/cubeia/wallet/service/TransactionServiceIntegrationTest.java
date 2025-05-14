package com.cubeia.wallet.service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
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
@DirtiesContext
public class TransactionServiceIntegrationTest {
    
    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private TransactionTemplate transactionTemplate; // Autowire actual Spring TransactionTemplate
    
    /**
     * Helper method to set account balance using reflection
     */
    private void setAccountBalance(Account account, BigDecimal balance) {
        try {
            Field balanceField = Account.class.getDeclaredField("balance");
            balanceField.setAccessible(true);
            balanceField.set(account, balance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set balance", e);
        }
    }
    
    @Test
    @Transactional
    void testInsufficientFundsExceptionHandling() {
        // Create accounts
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountBalance(fromAccount, new BigDecimal("50.00")); // Only 50 EUR
        accountRepository.save(fromAccount);
        
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountBalance(toAccount, BigDecimal.ZERO);
        accountRepository.save(toAccount);
        
        BigDecimal transferAmount = new BigDecimal("100.00"); // More than available
        
        // Verify the exception is thrown and contains the right message
        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class, () -> {
            transactionService.transfer(fromAccount.getId(), toAccount.getId(), transferAmount);
        });
        
        assertTrue(exception.getMessage().contains("Insufficient funds"));
        assertTrue(exception.getMessage().contains(fromAccount.getId().toString()));
    }
    
    @Test
    @Transactional
    void testOtherIllegalArgumentException() {
        // Create accounts
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountBalance(fromAccount, new BigDecimal("100.00"));
        accountRepository.save(fromAccount);
        
        Account toAccount = new Account(Currency.USD, AccountType.MainAccount.getInstance()); // Different currency
        setAccountBalance(toAccount, BigDecimal.ZERO);
        accountRepository.save(toAccount);
        
        BigDecimal transferAmount = new BigDecimal("50.00");
        
        // Attempt to transfer between accounts with different currencies
        CurrencyMismatchException exception = assertThrows(CurrencyMismatchException.class, () -> {
            transactionService.transfer(fromAccount.getId(), toAccount.getId(), transferAmount);
        });
        
        assertTrue(exception.getMessage().contains("different currencies"));
    }
    
    @Test
    void testTransactionTemplateIsolation() {
        // Create accounts
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountBalance(fromAccount, new BigDecimal("200.00"));
        fromAccount = accountRepository.save(fromAccount);
        
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountBalance(toAccount, new BigDecimal("50.00"));
        toAccount = accountRepository.save(toAccount);
        
        BigDecimal transferAmount = new BigDecimal("75.00");
        
        // Execute transfer and verify it completes atomically
        Transaction transaction = transactionService.transfer(
                fromAccount.getId(), toAccount.getId(), transferAmount);
        
        // Verify the transaction details
        assertNotNull(transaction);
        assertNotNull(transaction.getId());
        assertEquals(fromAccount.getId(), transaction.getFromAccountId());
        assertEquals(toAccount.getId(), transaction.getToAccountId());
        assertEquals(transferAmount, transaction.getAmount());
        
        // Reload accounts to get current state
        Account updatedFromAccount = accountRepository.findById(fromAccount.getId()).orElseThrow();
        Account updatedToAccount = accountRepository.findById(toAccount.getId()).orElseThrow();
        
        // Verify account balances
        assertEquals(0, new BigDecimal("125.00").compareTo(updatedFromAccount.getBalance()),
                "Source account balance should be 125.00");
        assertEquals(0, new BigDecimal("125.00").compareTo(updatedToAccount.getBalance()),
                "Destination account balance should be 125.00");
    }
    
    @Test
    void testIdempotencyWithTransactionTemplate() {
        // Create accounts
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountBalance(fromAccount, new BigDecimal("200.00"));
        fromAccount = accountRepository.save(fromAccount);
        
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountBalance(toAccount, new BigDecimal("50.00"));
        toAccount = accountRepository.save(toAccount);
        
        BigDecimal transferAmount = new BigDecimal("75.00");
        String referenceId = "IDEMPOTENCY-TEST-" + UUID.randomUUID();
        
        // Execute first transfer
        Transaction firstTransaction = transactionService.transfer(
                fromAccount.getId(), toAccount.getId(), transferAmount, referenceId);
        
        // Reload accounts to get current state after first transfer
        Account afterFirstFromAccount = accountRepository.findById(fromAccount.getId()).orElseThrow();
        Account afterFirstToAccount = accountRepository.findById(toAccount.getId()).orElseThrow();
        
        // Execute second transfer with same reference ID
        Transaction secondTransaction = transactionService.transfer(
                fromAccount.getId(), toAccount.getId(), transferAmount, referenceId);
        
        // Verify it's the same transaction
        assertEquals(firstTransaction.getId(), secondTransaction.getId());
        
        // Reload accounts to get current state after second transfer attempt
        Account afterSecondFromAccount = accountRepository.findById(fromAccount.getId()).orElseThrow();
        Account afterSecondToAccount = accountRepository.findById(toAccount.getId()).orElseThrow();
        
        // Verify account balances haven't changed after second attempt
        assertEquals(afterFirstFromAccount.getBalance(), afterSecondFromAccount.getBalance());
        assertEquals(afterFirstToAccount.getBalance(), afterSecondToAccount.getBalance());
        
        // Verify final balances
        assertEquals(0, new BigDecimal("125.00").compareTo(afterSecondFromAccount.getBalance()),
                "Source account balance should be 125.00");
        assertEquals(0, new BigDecimal("125.00").compareTo(afterSecondToAccount.getBalance()),
                "Destination account balance should be 125.00");
    }
} 