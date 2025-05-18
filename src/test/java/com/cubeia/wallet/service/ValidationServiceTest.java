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
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
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
class ValidationServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private DoubleEntryService doubleEntryService;

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
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set ID", e);
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
        
        toAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(toAccount, toAccountId);
        
        // Mock the DoubleEntryService to return our expected balances - using lenient to avoid UnnecessaryStubbingException
        lenient().when(doubleEntryService.calculateBalance(eq(fromAccountId))).thenReturn(new BigDecimal("500.00"));
        lenient().when(doubleEntryService.calculateBalance(eq(toAccountId))).thenReturn(new BigDecimal("200.00"));
    }
    
    @Test
    void validateTransferParameters_ShouldReturnAccountsWhenValid() {
        // Arrange
        lenient().when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        lenient().when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        lenient().when(transactionRepository.findByReferenceIgnoreCase(referenceId)).thenReturn(Optional.empty());
        
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
        lenient().when(accountRepository.findById(fromAccountId)).thenReturn(Optional.empty());
        
        // Act & Assert
        AccountNotFoundException exception = assertThrows(AccountNotFoundException.class, () -> {
            validationService.validateTransferParameters(fromAccountId, toAccountId, amount, referenceId);
        });
        assertNotNull(exception.getMessage());
    }
    
    @Test
    void validateTransferParameters_ShouldThrowWhenToAccountNotFound() {
        // Arrange
        lenient().when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        lenient().when(accountRepository.findById(toAccountId)).thenReturn(Optional.empty());
        
        // Act & Assert
        AccountNotFoundException exception = assertThrows(AccountNotFoundException.class, () -> {
            validationService.validateTransferParameters(fromAccountId, toAccountId, amount, referenceId);
        });
        assertNotNull(exception.getMessage());
    }
    
    @Test
    void validateTransferParameters_ShouldThrowOnCurrencyMismatch() {
        // Arrange
        Account usdAccount = new Account(Currency.USD, AccountType.MainAccount.getInstance());
        setAccountId(usdAccount, toAccountId);
        
        lenient().when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        lenient().when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(usdAccount));
        
        // Act & Assert
        CurrencyMismatchException exception = assertThrows(CurrencyMismatchException.class, () -> {
            validationService.validateTransferParameters(fromAccountId, toAccountId, amount, referenceId);
        });
        assertNotNull(exception.getMessage());
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
        
        lenient().when(transactionRepository.findByReferenceIgnoreCase(referenceId)).thenReturn(Optional.of(existingTransaction));
        
        // Act & Assert
        InvalidTransactionException exception = assertThrows(InvalidTransactionException.class, () -> {
            validationService.validateTransferParameters(fromAccountId, toAccountId, amount, referenceId);
        });
        assertNotNull(exception.getMessage());
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
        
        lenient().when(transactionRepository.findByReferenceIgnoreCase(referenceId)).thenReturn(Optional.of(existingTransaction));
        lenient().when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        lenient().when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        
        // Act
        TransferValidationResult result = validationService.validateTransferParameters(
                fromAccountId, toAccountId, amount, referenceId);
        
        // Assert
        assertNotNull(result);
        assertEquals(null, result.fromAccount());  // Accounts are not fetched for idempotent transactions
        assertEquals(null, result.toAccount());    // Accounts are not fetched for idempotent transactions
        assertEquals(existingTransaction, result.existingTransaction());
    }
    
    @Test
    void validateSufficientFunds_ShouldThrowOnInsufficientFunds() {
        // Arrange
        BigDecimal largeAmount = new BigDecimal("1000.00");
        
        // Act & Assert
        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class, () -> {
            validationService.validateSufficientFunds(fromAccount, largeAmount);
        });
        assertNotNull(exception.getMessage());
    }
    
    @Test
    void validateSufficientFunds_ShouldPassWithSufficientFunds() {
        // Arrange - using the default setup with 500.00 balance
        
        // Act & Assert - No exception should be thrown
        validationService.validateSufficientFunds(fromAccount, amount);
    }
    
    @Test
    void validateSufficientFunds_ShouldRespectAccountType() {
        // Arrange
        Account systemAccount = new Account(Currency.EUR, AccountType.SystemAccount.getInstance());
        setAccountId(systemAccount, UUID.randomUUID());
        
        BigDecimal largeAmount = new BigDecimal("1000000.00");  // Very large amount
        
        // Act & Assert - No exception should be thrown since system accounts have unlimited funds
        validationService.validateSufficientFunds(systemAccount, largeAmount);
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
        
        // Act & Assert - No exception should be thrown
        validationService.validateExistingTransactionMatch(existingTransaction, fromAccountId, toAccountId, amount);
    }
    
    @Test
    void validateExistingTransactionMatch_ShouldThrowWhenFromAccountDiffers() {
        // Arrange
        UUID differentAccountId = UUID.randomUUID();
        Transaction existingTransaction = new Transaction(
                differentAccountId,
                toAccountId,
                amount,
                TransactionType.TRANSFER,
                Currency.EUR,
                referenceId
        );
        
        // Act & Assert
        InvalidTransactionException exception = assertThrows(InvalidTransactionException.class, () -> {
            validationService.validateExistingTransactionMatch(existingTransaction, fromAccountId, toAccountId, amount);
        });
        assertNotNull(exception.getMessage());
    }
    
    @Test
    void validateExistingTransactionMatch_ShouldThrowWhenToAccountDiffers() {
        // Arrange
        UUID differentAccountId = UUID.randomUUID();
        Transaction existingTransaction = new Transaction(
                fromAccountId,
                differentAccountId,
                amount,
                TransactionType.TRANSFER,
                Currency.EUR,
                referenceId
        );
        
        // Act & Assert
        InvalidTransactionException exception = assertThrows(InvalidTransactionException.class, () -> {
            validationService.validateExistingTransactionMatch(existingTransaction, fromAccountId, toAccountId, amount);
        });
        assertNotNull(exception.getMessage());
    }
    
    @Test
    void validateExistingTransactionMatch_ShouldThrowWhenAmountDiffers() {
        // Arrange
        BigDecimal differentAmount = new BigDecimal("200.00");
        Transaction existingTransaction = new Transaction(
                fromAccountId,
                toAccountId,
                differentAmount,
                TransactionType.TRANSFER,
                Currency.EUR,
                referenceId
        );
        
        // Act & Assert
        InvalidTransactionException exception = assertThrows(InvalidTransactionException.class, () -> {
            validationService.validateExistingTransactionMatch(existingTransaction, fromAccountId, toAccountId, amount);
        });
        assertNotNull(exception.getMessage());
    }
} 