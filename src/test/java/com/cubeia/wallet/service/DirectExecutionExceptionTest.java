package com.cubeia.wallet.service;

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

/**
 * Test that directly exercises the Transaction.execute to trigger exceptions
 * for coverage of the TransactionService exception handling.
 */
@SpringBootTest(classes = WalletApplication.class)
public class DirectExecutionExceptionTest {

    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private AccountRepository accountRepository;
    
    /**
     * Helper method to set a private field via reflection
     */
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
    
    @Test
    @Transactional
    public void testExecuteWithInsufficientFunds() throws Exception {
        // Save the accounts to get IDs
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        accountRepository.save(fromAccount);
        
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        accountRepository.save(toAccount);
        
        // Set a very small balance that will trigger insufficientFunds
        setField(fromAccount, "balance", new BigDecimal("5.00"));
        accountRepository.save(fromAccount);
        
        // Create the transaction with an amount larger than the balance
        BigDecimal amount = new BigDecimal("10.00");
        Transaction transaction = new Transaction(
            fromAccount.getId(), 
            toAccount.getId(),
            amount,
            TransactionType.TRANSFER,
            Currency.EUR
        );
        
        // This should directly trigger the InsufficientFundsException 
        // when transaction.execute is called inside transferService
        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class, () -> {
            transactionService.transfer(fromAccount.getId(), toAccount.getId(), amount);
        });
        
        assertTrue(exception.getMessage().contains("exceeds maximum withdrawal amount"));
    }
    
    @Test
    @Transactional
    public void testDirectExecuteWithInsufficientFunds() throws Exception {
        // Create accounts
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setField(fromAccount, "id", fromId);
        setField(fromAccount, "balance", new BigDecimal("5.00"));
        
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setField(toAccount, "id", toId);
        
        // Create transaction with amount greater than balance
        BigDecimal amount = new BigDecimal("10.00");
        Transaction transaction = new Transaction(
            fromId, 
            toId,
            amount,
            TransactionType.TRANSFER,
            Currency.EUR
        );
        
        // Call execute directly to trigger IllegalArgumentException - this simulates 
        // what happens inside TransactionService.transfer that we want to cover
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transaction.execute(transaction, fromAccount, toAccount);
        });
        
        assertTrue(exception.getMessage().contains("Insufficient funds"));
    }
} 