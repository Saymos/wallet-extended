package com.cubeia.wallet.service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.exception.CurrencyMismatchException;
import com.cubeia.wallet.exception.InsufficientFundsException;
import com.cubeia.wallet.exception.InvalidTransactionException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.TransactionRepository;
import com.cubeia.wallet.service.ValidationService.TransferValidationResult;

@ExtendWith(MockitoExtension.class)
public class ValidationServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private ValidationService validationService;
    
    private UUID fromAccountId;
    private UUID toAccountId;
    private Account fromAccount;
    private Account toAccount;
    private BigDecimal amount;
    private String referenceId;
    
    /**
     * Helper method to set account ID using reflection (for testing)
     */
    private void setAccountId(Account account, UUID id) {
        try {
            Field idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(account, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID", e);
        }
    }
    
    /**
     * Helper method to set account balance using reflection (for testing)
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
    
    @BeforeEach
    void setup() {
        fromAccountId = UUID.randomUUID();
        toAccountId = UUID.randomUUID();
        amount = new BigDecimal("100.00");
        referenceId = "TEST-REF-001";
        
        fromAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(fromAccount, fromAccountId);
        setAccountBalance(fromAccount, new BigDecimal("500.00"));
        
        toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        setAccountBalance(toAccount, new BigDecimal("200.00"));
    }
    
    @Test
    void validateTransferParameters_ShouldReturnAccountsWhenValid() {
        // Arrange
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        when(transactionRepository.findByReference(referenceId)).thenReturn(Optional.empty());
        
        // Act
        TransferValidationResult result = validationService.validateTransferParameters(
                fromAccountId, toAccountId, amount, referenceId);
        
        // Assert
        assertNotNull(result);
        assertEquals(fromAccount, result.fromAccount());
        assertEquals(toAccount, result.toAccount());
    }
    
    @Test
    void validateTransferParameters_ShouldThrowWhenFromAccountNotFound() {
        // Arrange
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            validationService.validateTransferParameters(fromAccountId, toAccountId, amount, referenceId);
        });
    }
    
    @Test
    void validateTransferParameters_ShouldThrowWhenToAccountNotFound() {
        // Arrange
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            validationService.validateTransferParameters(fromAccountId, toAccountId, amount, referenceId);
        });
    }
    
    @Test
    void validateTransferParameters_ShouldThrowOnCurrencyMismatch() {
        // Arrange
        Account usdAccount = new Account(Currency.USD, AccountType.MainAccount.getInstance());
        setAccountId(usdAccount, toAccountId);
        
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(usdAccount));
        
        // Act & Assert
        assertThrows(CurrencyMismatchException.class, () -> {
            validationService.validateTransferParameters(fromAccountId, toAccountId, amount, referenceId);
        });
    }
    
    @Test
    void validateTransferParameters_ShouldThrowOnDuplicateReferenceWithDifferentParameters() {
        // Arrange
        Transaction existingTransaction = new Transaction(
                fromAccountId,
                toAccountId,
                new BigDecimal("50.00"), // Different amount
                TransactionType.TRANSFER,
                Currency.EUR,
                referenceId
        );
        
        when(transactionRepository.findByReference(referenceId)).thenReturn(Optional.of(existingTransaction));
        
        // Act & Assert
        assertThrows(InvalidTransactionException.class, () -> {
            validationService.validateTransferParameters(fromAccountId, toAccountId, amount, referenceId);
        });
    }
    
    @Test
    void validateTransferParameters_ShouldPassWithIdenticalDuplicateTransaction() {
        // Arrange
        Transaction existingTransaction = new Transaction(
                fromAccountId,
                toAccountId,
                amount, // Same amount
                TransactionType.TRANSFER,
                Currency.EUR,
                referenceId
        );
        
        when(transactionRepository.findByReference(referenceId)).thenReturn(Optional.of(existingTransaction));
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        
        // Act
        TransferValidationResult result = validationService.validateTransferParameters(
                fromAccountId, toAccountId, amount, referenceId);
        
        // Assert
        assertNotNull(result);
        assertEquals(fromAccount, result.fromAccount());
        assertEquals(toAccount, result.toAccount());
    }
    
    @Test
    void validateSufficientFunds_ShouldThrowOnInsufficientFunds() {
        // Arrange
        BigDecimal largeAmount = new BigDecimal("1000.00");
        
        // Act & Assert
        assertThrows(InsufficientFundsException.class, () -> {
            validationService.validateSufficientFunds(fromAccount, largeAmount);
        });
    }
    
    @Test
    void validateSufficientFunds_ShouldPassWithSufficientFunds() {
        // Arrange
        BigDecimal smallAmount = new BigDecimal("100.00");
        
        // Act
        validationService.validateSufficientFunds(fromAccount, smallAmount);
        
        // No assertion needed - if no exception is thrown, the test passes
    }
    
    @Test
    void validateSufficientFunds_ShouldRespectAccountType() {
        // Arrange
        Account bonusAccount = new Account(Currency.EUR, AccountType.BonusAccount.getInstance());
        setAccountId(bonusAccount, UUID.randomUUID());
        setAccountBalance(bonusAccount, new BigDecimal("500.00"));
        
        // Act & Assert
        assertThrows(InsufficientFundsException.class, () -> {
            validationService.validateSufficientFunds(bonusAccount, new BigDecimal("1.00"));
        });
    }
    
    @Test
    void validateExistingTransactionMatch_ShouldPassWhenParametersMatch() {
        // Arrange
        Transaction existingTransaction = new Transaction(
                fromAccountId,
                toAccountId,
                amount,
                TransactionType.TRANSFER,
                Currency.EUR,
                referenceId
        );
        
        // Act
        validationService.validateExistingTransactionMatch(
                existingTransaction, fromAccountId, toAccountId, amount);
        
        // No assertion needed - if no exception is thrown, the test passes
    }
    
    @Test
    void validateExistingTransactionMatch_ShouldThrowWhenFromAccountDiffers() {
        // Arrange
        UUID differentAccountId = UUID.randomUUID();
        Transaction existingTransaction = new Transaction(
                differentAccountId, // Different from account
                toAccountId,
                amount,
                TransactionType.TRANSFER,
                Currency.EUR,
                referenceId
        );
        
        // Act & Assert
        assertThrows(InvalidTransactionException.class, () -> {
            validationService.validateExistingTransactionMatch(
                    existingTransaction, fromAccountId, toAccountId, amount);
        });
    }
    
    @Test
    void validateExistingTransactionMatch_ShouldThrowWhenToAccountDiffers() {
        // Arrange
        UUID differentAccountId = UUID.randomUUID();
        Transaction existingTransaction = new Transaction(
                fromAccountId,
                differentAccountId, // Different to account
                amount,
                TransactionType.TRANSFER,
                Currency.EUR,
                referenceId
        );
        
        // Act & Assert
        assertThrows(InvalidTransactionException.class, () -> {
            validationService.validateExistingTransactionMatch(
                    existingTransaction, fromAccountId, toAccountId, amount);
        });
    }
    
    @Test
    void validateExistingTransactionMatch_ShouldThrowWhenAmountDiffers() {
        // Arrange
        Transaction existingTransaction = new Transaction(
                fromAccountId,
                toAccountId,
                new BigDecimal("50.00"), // Different amount
                TransactionType.TRANSFER,
                Currency.EUR,
                referenceId
        );
        
        // Act & Assert
        assertThrows(InvalidTransactionException.class, () -> {
            validationService.validateExistingTransactionMatch(
                    existingTransaction, fromAccountId, toAccountId, amount);
        });
    }
} 