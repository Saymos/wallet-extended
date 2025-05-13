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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.repository.AccountRepository;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

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

    @Test
    void createAccount_ShouldCreateAccountWithZeroBalanceAndDefaultCurrency() {
        // Arrange
        Account mockAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(mockAccount, 1L);
        
        when(accountRepository.save(any(Account.class))).thenReturn(mockAccount);

        // Act
        Account result = accountService.createAccount();

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(BigDecimal.ZERO, result.getBalance());
        assertEquals(Currency.EUR, result.getCurrency());
        assertEquals(AccountType.MainAccount.getInstance(), result.getAccountType());
        verify(accountRepository, times(1)).save(any(Account.class));
    }
    
    @Test
    void createAccount_ShouldCreateAccountWithSpecificCurrencyAndType() {
        // Arrange
        Currency currency = Currency.USD;
        AccountType accountType = AccountType.BonusAccount.getInstance();
        
        // Setup a return value for the repository mock
        Account mockAccount = new Account(currency, accountType);
        setAccountId(mockAccount, 1L);
        
        // Capture the actual account being saved to verify its properties
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        when(accountRepository.save(accountCaptor.capture())).thenReturn(mockAccount);

        // Act
        Account result = accountService.createAccount(currency, accountType);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(BigDecimal.ZERO, result.getBalance());
        assertEquals(currency, result.getCurrency());
        assertEquals(accountType, result.getAccountType());
        
        // Verify that the correct account properties were passed to save
        Account capturedAccount = accountCaptor.getValue();
        assertEquals(currency, capturedAccount.getCurrency());
        assertEquals(accountType, capturedAccount.getAccountType());
        
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void getBalance_ShouldReturnCorrectBalance() {
        // Arrange
        Long accountId = 1L;
        BigDecimal expectedBalance = BigDecimal.valueOf(100.0);
        
        Account account = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(account, accountId);
        
        // Set balance using reflection
        try {
            Field balanceField = Account.class.getDeclaredField("balance");
            balanceField.setAccessible(true);
            balanceField.set(account, expectedBalance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set balance", e);
        }
        
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // Act
        BigDecimal result = accountService.getBalance(accountId);

        // Assert
        assertEquals(expectedBalance, result);
        verify(accountRepository, times(1)).findById(accountId);
    }

    @Test
    void getBalance_ShouldThrowAccountNotFoundException() {
        // Arrange
        Long accountId = 999L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            accountService.getBalance(accountId);
        });
        verify(accountRepository, times(1)).findById(accountId);
    }
    
    @Test
    void getAccount_ShouldReturnAccountWithCorrectCurrencyAndType() {
        // Arrange
        Long accountId = 1L;
        Currency expectedCurrency = Currency.CHF;
        AccountType expectedAccountType = AccountType.JackpotAccount.getInstance();
        
        Account account = new Account(expectedCurrency, expectedAccountType);
        setAccountId(account, accountId);
        
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // Act
        Account result = accountService.getAccount(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getId());
        assertEquals(expectedCurrency, result.getCurrency());
        assertEquals(expectedAccountType, result.getAccountType());
        verify(accountRepository, times(1)).findById(accountId);
    }
} 