package com.cubeia.wallet.service;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.cubeia.wallet.WalletApplication;
import com.cubeia.wallet.exception.CurrencyMismatchException;
import com.cubeia.wallet.exception.InsufficientFundsException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.repository.AccountRepository;

/**
 * This class tests the TransactionService's exception handling directly without mocking.
 * We'll use a special implementation of Transaction with exceptionally small balance
 * to trigger the exception handling code.
 */
@SpringBootTest(classes = WalletApplication.class)
class TransactionDirectTest {

    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Test
    @Transactional
    void testInsufficientFundsExceptionHandling() {
        // Create account with minimum balance and save to DB
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        accountRepository.save(fromAccount);
        
        // Create a second account
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        accountRepository.save(toAccount);
        
        // Now try to transfer more than the balance (which is 0)
        BigDecimal amount = new BigDecimal("100.00");
        
        // This should trigger the exception handling in TransactionService
        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class, () -> {
            transactionService.transfer(fromAccount.getId(), toAccount.getId(), amount, null, null);
        });
        
        // Verify the exception contains expected information
        assertTrue(exception.getMessage().contains("Insufficient funds"), 
            "Exception message should mention 'Insufficient funds'");
    }
    
    @Test
    @Transactional
    void testCurrencyMismatchException() {
        // Create accounts with different currencies
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        accountRepository.save(fromAccount);
        
        Account toAccount = new Account(Currency.USD, AccountType.MainAccount.getInstance());
        accountRepository.save(toAccount);
        
        // Try to transfer between mismatched currencies
        BigDecimal amount = new BigDecimal("100.00");
        
        // This should trigger a currency mismatch exception
        CurrencyMismatchException exception = assertThrows(CurrencyMismatchException.class, () -> {
            transactionService.transfer(fromAccount.getId(), toAccount.getId(), amount, null, null);
        });
        
        // Verify the exception contains expected information
        assertTrue(exception.getMessage().contains("different currencies"));
    }
} 