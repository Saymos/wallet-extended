package com.cubeia.wallet.service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.repository.AccountRepository;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    
    @Mock
    private DoubleEntryService doubleEntryService;

    @InjectMocks
    private AccountService accountService;
    
    /**
     * Helper method to set account ID using reflection (for testing)
     */
    private void setAccountId(Account account, UUID id) {
        try {
            Field idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(account, id);
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException e) {
            throw new RuntimeException("Failed to set ID", e);
        }
    }

    @Test
    void createAccount_ShouldCreateAccountWithZeroBalanceAndDefaultCurrency() {
        // Arrange
        Account mockAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        UUID accountId = UUID.randomUUID();
        setAccountId(mockAccount, accountId);
        
        when(accountRepository.save(any(Account.class))).thenReturn(mockAccount);

        // Act
        Account result = accountService.createAccount();

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getId());
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
        UUID accountId = UUID.randomUUID();
        setAccountId(mockAccount, accountId);
        
        // Capture the actual account being saved to verify its properties
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        when(accountRepository.save(accountCaptor.capture())).thenReturn(mockAccount);

        // Act
        Account result = accountService.createAccount(currency, accountType);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getId());
        assertEquals(currency, result.getCurrency());
        assertEquals(accountType, result.getAccountType());
        
        // Verify that the correct account properties were passed to save
        Account capturedAccount = accountCaptor.getValue();
        assertEquals(currency, capturedAccount.getCurrency());
        assertEquals(accountType, capturedAccount.getAccountType());
        
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void getAccount_ShouldReturnAccountWithCorrectCurrencyAndType() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        Currency expectedCurrency = Currency.CHF;
        AccountType expectedAccountType = AccountType.JackpotAccount.getInstance();
        
        Account account = new Account(expectedCurrency, expectedAccountType);
        setAccountId(account, accountId);
        
        // The getAccount method only uses findById, so we only need to mock that
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
    
    @Test
    void getBalance_ShouldReturnBalanceFromDoubleEntryService() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        BigDecimal expectedBalance = new BigDecimal("123.45");
        
        // Mock repository to confirm account exists
        when(accountRepository.existsById(accountId)).thenReturn(true);
        
        // Mock doubleEntryService to return the expected balance
        when(doubleEntryService.calculateBalance(accountId)).thenReturn(expectedBalance);
        
        // Act
        BigDecimal balance = accountService.getBalance(accountId);
        
        // Assert
        assertEquals(expectedBalance, balance);
        verify(accountRepository).existsById(accountId);
        verify(doubleEntryService).calculateBalance(accountId);
    }
    
    @Test
    void getMaxWithdrawalAmount_ShouldReturnFullBalanceForMainAccount() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        BigDecimal currentBalance = new BigDecimal("500.00");
        
        Account account = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        setAccountId(account, accountId);
        
        // Mock repository to return the account
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        
        // Mock doubleEntryService to return the current balance
        when(doubleEntryService.calculateBalance(accountId)).thenReturn(currentBalance);
        
        // Act
        BigDecimal maxWithdrawal = accountService.getMaxWithdrawalAmount(accountId);
        
        // Assert
        assertEquals(currentBalance, maxWithdrawal);
        verify(accountRepository).findById(accountId);
        verify(doubleEntryService).calculateBalance(accountId);
    }
} 