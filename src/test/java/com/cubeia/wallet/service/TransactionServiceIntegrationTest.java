package com.cubeia.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.cubeia.wallet.WalletApplication;
import com.cubeia.wallet.exception.InsufficientFundsException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.TransactionRepository;

/**
 * Integration tests for TransactionService that tests
 * edge cases involving InsufficientFundsException handling.
 */
@SpringBootTest(classes = WalletApplication.class)
@Transactional
public class TransactionServiceIntegrationTest {
    
    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
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
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transactionService.transfer(fromAccount.getId(), toAccount.getId(), transferAmount);
        });
        
        assertTrue(exception.getMessage().contains("Currency mismatch"));
    }
} 