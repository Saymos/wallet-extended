package com.cubeia.wallet.service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.lenient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.cubeia.wallet.WalletApplication;
import com.cubeia.wallet.exception.InsufficientFundsException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.LedgerEntryRepository;
import com.cubeia.wallet.repository.TransactionRepository;

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
            transactionService.transfer(fromAccount.getId(), toAccount.getId(), amount);
        });
        
        assertTrue(exception.getMessage().contains("Insufficient funds"));
    }
    
    @Test
    @Transactional
    public void testDirectExecuteWithInsufficientFunds() throws Exception {
        // Create accounts
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        
        Account fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setField(fromAccount, "id", fromId);
        
        Account toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setField(toAccount, "id", toId);
        
        // Create a test service with mocks to avoid DB interaction
        AccountRepository mockRepo = org.mockito.Mockito.mock(AccountRepository.class);
        TransactionRepository mockTxRepo = org.mockito.Mockito.mock(TransactionRepository.class);
        ValidationService mockValidation = org.mockito.Mockito.mock(ValidationService.class);
        LedgerEntryRepository mockLedgerRepo = org.mockito.Mockito.mock(LedgerEntryRepository.class);
        org.springframework.transaction.PlatformTransactionManager mockTxManager = 
            org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
            
        // Create DoubleEntryService with mocks
        DoubleEntryService mockDoubleEntryService = new DoubleEntryService(mockLedgerRepo, mockRepo);
            
        // Use lenient mocking to avoid UnnecessaryStubbing errors
        lenient().when(mockRepo.findById(fromId)).thenReturn(java.util.Optional.of(fromAccount));
        lenient().when(mockRepo.findById(toId)).thenReturn(java.util.Optional.of(toAccount));
        lenient().when(mockRepo.existsById(fromId)).thenReturn(true);
        lenient().when(mockRepo.existsById(toId)).thenReturn(true);
        
        // Mock the ledger entry repository to return a balance of 5.00
        lenient().when(mockLedgerRepo.calculateBalance(fromId)).thenReturn(new BigDecimal("5.00"));
        lenient().when(mockLedgerRepo.calculateBalance(toId)).thenReturn(BigDecimal.ZERO);
        
        // Connect the doubleEntryService to the accounts
        fromAccount.setDoubleEntryService(mockDoubleEntryService);
        toAccount.setDoubleEntryService(mockDoubleEntryService);
        
        // Mock ValidationService to pass validation
        ValidationService.TransferValidationResult validationResult = 
            new ValidationService.TransferValidationResult(fromAccount, toAccount, null);
        lenient().when(mockValidation.validateTransferParameters(fromId, toId, new BigDecimal("10.00"), null))
            .thenReturn(validationResult);
            
        // Set up transaction template for testing
        TransactionService testService = new TransactionService(
                mockRepo, mockTxRepo, mockValidation, mockTxManager, mockDoubleEntryService) {
            @Override
            protected org.springframework.transaction.support.TransactionTemplate getTransactionTemplate() {
                return new org.springframework.transaction.support.TransactionTemplate(mockTxManager) {
                    @Override
                    public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                        return action.doInTransaction(null);
                    }
                };
            }
        };
        
        // Call transfer to trigger InsufficientFundsException
        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class, () -> {
            testService.transfer(fromId, toId, new BigDecimal("10.00"));
        });
        
        assertTrue(exception.getMessage().contains("Insufficient funds"));
    }
} 