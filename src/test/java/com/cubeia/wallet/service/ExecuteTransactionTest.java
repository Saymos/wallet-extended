package com.cubeia.wallet.service;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.cubeia.wallet.WalletApplication;
import com.cubeia.wallet.exception.InsufficientFundsException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.repository.AccountRepository;

/**
 * Test that directly exercises the TransactionService.transfer method
 * to verify exception handling.
 */
@SpringBootTest(classes = WalletApplication.class)
public class ExecuteTransactionTest {

    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private DoubleEntryService doubleEntryService;
    
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
        
        // Set a very small balance via a ledger entry
        doubleEntryService.createSystemCreditEntry(
            fromAccount.getId(),
            new BigDecimal("5.00"),
            "Test initial balance"
        );
        
        // Create the transaction with an amount larger than the balance
        BigDecimal amount = new BigDecimal("10.00");
        
        // This should directly trigger the InsufficientFundsException when transfer is called
        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class, () -> {
            transactionService.transfer(fromAccount.getId(), toAccount.getId(), amount, null, null);
        });
        
        assertTrue(exception.getMessage().contains("Insufficient funds"));
    }
} 