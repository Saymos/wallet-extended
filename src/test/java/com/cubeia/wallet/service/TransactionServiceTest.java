package com.cubeia.wallet.service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.exception.InsufficientFundsException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;
    
    /**
     * Helper method to set account ID using reflection (for testing)
     */
    private void setAccountId(Account account, Long id) {
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

    @Test
    void transfer_ShouldSuccessfullyTransferFunds() {
        // Arrange
        Long fromAccountId = 1L;
        Long toAccountId = 2L;
        BigDecimal amount = new BigDecimal("100.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MAIN);
        setAccountId(fromAccount, fromAccountId);
        setAccountBalance(fromAccount, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.EUR, AccountType.MAIN);
        setAccountId(toAccount, toAccountId);
        setAccountBalance(toAccount, new BigDecimal("50.00"));

        when(accountRepository.findByIdWithLock(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdWithLock(toAccountId)).thenReturn(Optional.of(toAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction savedTransaction = invocation.getArgument(0);
            // Use reflection to set transaction ID
            try {
                Field idField = Transaction.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(savedTransaction, 1L);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set transaction ID", e);
            }
            return savedTransaction;
        });

        // Act
        Transaction result = transactionService.transfer(fromAccountId, toAccountId, amount);

        // Assert
        assertNotNull(result);
        assertEquals(fromAccountId, result.getFromAccountId());
        assertEquals(toAccountId, result.getToAccountId());
        assertEquals(amount, result.getAmount());
        assertEquals(Currency.EUR, result.getCurrency());
        assertEquals(TransactionType.TRANSFER, result.getTransactionType());

        assertEquals(new BigDecimal("100.00"), fromAccount.getBalance());
        assertEquals(new BigDecimal("150.00"), toAccount.getBalance());

        verify(accountRepository, times(1)).findByIdWithLock(fromAccountId);
        verify(accountRepository, times(1)).findByIdWithLock(toAccountId);
        verify(accountRepository, times(2)).save(any(Account.class));
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void transfer_ShouldThrowInsufficientFundsException() {
        // Arrange
        Long fromAccountId = 1L;
        Long toAccountId = 2L;
        BigDecimal amount = new BigDecimal("300.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MAIN);
        setAccountId(fromAccount, fromAccountId);
        setAccountBalance(fromAccount, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.EUR, AccountType.MAIN);
        setAccountId(toAccount, toAccountId);
        setAccountBalance(toAccount, new BigDecimal("50.00"));

        when(accountRepository.findByIdWithLock(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdWithLock(toAccountId)).thenReturn(Optional.of(toAccount));

        // Act & Assert
        assertThrows(InsufficientFundsException.class, () -> {
            transactionService.transfer(fromAccountId, toAccountId, amount);
        });

        // Verify that balances weren't changed
        assertEquals(new BigDecimal("200.00"), fromAccount.getBalance());
        assertEquals(new BigDecimal("50.00"), toAccount.getBalance());

        verify(accountRepository, times(1)).findByIdWithLock(fromAccountId);
        verify(accountRepository, times(1)).findByIdWithLock(toAccountId);
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_ShouldThrowAccountNotFoundExceptionForSender() {
        // Arrange
        Long fromAccountId = 1L;
        Long toAccountId = 2L;
        BigDecimal amount = new BigDecimal("100.00");

        when(accountRepository.findByIdWithLock(fromAccountId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            transactionService.transfer(fromAccountId, toAccountId, amount);
        });

        verify(accountRepository, times(1)).findByIdWithLock(fromAccountId);
        verify(accountRepository, never()).findByIdWithLock(toAccountId);
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_ShouldThrowAccountNotFoundExceptionForReceiver() {
        // Arrange
        Long fromAccountId = 1L;
        Long toAccountId = 2L;
        BigDecimal amount = new BigDecimal("100.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MAIN);
        setAccountId(fromAccount, fromAccountId);
        setAccountBalance(fromAccount, new BigDecimal("200.00"));

        when(accountRepository.findByIdWithLock(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdWithLock(toAccountId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            transactionService.transfer(fromAccountId, toAccountId, amount);
        });

        // Verify that sender's balance wasn't changed
        assertEquals(new BigDecimal("200.00"), fromAccount.getBalance());

        verify(accountRepository, times(1)).findByIdWithLock(fromAccountId);
        verify(accountRepository, times(1)).findByIdWithLock(toAccountId);
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_ShouldRejectNegativeAmount() {
        // Arrange
        Long fromAccountId = 1L;
        Long toAccountId = 2L;
        BigDecimal amount = new BigDecimal("-50.00");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            transactionService.transfer(fromAccountId, toAccountId, amount);
        });

        verify(accountRepository, never()).findByIdWithLock(anyLong());
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_ShouldRejectZeroAmount() {
        // Arrange
        Long fromAccountId = 1L;
        Long toAccountId = 2L;
        BigDecimal amount = BigDecimal.ZERO;

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            transactionService.transfer(fromAccountId, toAccountId, amount);
        });

        verify(accountRepository, never()).findByIdWithLock(anyLong());
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
    
    @Test
    void transfer_ShouldRejectCurrencyMismatch() {
        // Arrange
        Long fromAccountId = 1L;
        Long toAccountId = 2L;
        BigDecimal amount = new BigDecimal("100.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MAIN);
        setAccountId(fromAccount, fromAccountId);
        setAccountBalance(fromAccount, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.USD, AccountType.MAIN);
        setAccountId(toAccount, toAccountId);
        setAccountBalance(toAccount, new BigDecimal("50.00"));

        when(accountRepository.findByIdWithLock(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdWithLock(toAccountId)).thenReturn(Optional.of(toAccount));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transactionService.transfer(fromAccountId, toAccountId, amount);
        });
        
        assertEquals("Currency mismatch: Cannot transfer between accounts with different currencies", exception.getMessage());

        // Verify that balances weren't changed
        assertEquals(new BigDecimal("200.00"), fromAccount.getBalance());
        assertEquals(new BigDecimal("50.00"), toAccount.getBalance());

        verify(accountRepository, times(1)).findByIdWithLock(fromAccountId);
        verify(accountRepository, times(1)).findByIdWithLock(toAccountId);
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
    
    @Test
    void transfer_ShouldCreateCorrectTransactionWithCurrencyAndType() {
        // Arrange
        Long fromAccountId = 1L;
        Long toAccountId = 2L;
        BigDecimal amount = new BigDecimal("100.00");

        Account fromAccount = new Account(Currency.EUR, AccountType.MAIN);
        setAccountId(fromAccount, fromAccountId);
        setAccountBalance(fromAccount, new BigDecimal("200.00"));

        Account toAccount = new Account(Currency.EUR, AccountType.BONUS);
        setAccountId(toAccount, toAccountId);
        setAccountBalance(toAccount, new BigDecimal("50.00"));

        when(accountRepository.findByIdWithLock(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdWithLock(toAccountId)).thenReturn(Optional.of(toAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        when(transactionRepository.save(transactionCaptor.capture())).thenAnswer(invocation -> {
            Transaction savedTransaction = invocation.getArgument(0);
            // Use reflection to set transaction ID
            try {
                Field idField = Transaction.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(savedTransaction, 1L);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set transaction ID", e);
            }
            return savedTransaction;
        });

        // Act
        Transaction result = transactionService.transfer(fromAccountId, toAccountId, amount);

        // Assert
        assertNotNull(result);
        assertEquals(fromAccountId, result.getFromAccountId());
        assertEquals(toAccountId, result.getToAccountId());
        assertEquals(amount, result.getAmount());
        
        // Verify transaction was created with correct currency and type
        Transaction capturedTransaction = transactionCaptor.getValue();
        assertEquals(Currency.EUR, capturedTransaction.getCurrency());
        assertEquals(TransactionType.TRANSFER, capturedTransaction.getTransactionType());

        assertEquals(new BigDecimal("100.00"), fromAccount.getBalance());
        assertEquals(new BigDecimal("150.00"), toAccount.getBalance());
    }
} 