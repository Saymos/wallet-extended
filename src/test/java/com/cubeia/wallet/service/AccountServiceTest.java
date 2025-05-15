package com.cubeia.wallet.service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.repository.AccountRepository;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    
    @Mock
    private DoubleEntryService doubleEntryService;

    @InjectMocks
    private AccountService accountService;
    
    @BeforeEach
    void setUp() {
        // Use lenient() for all mocks to avoid UnnecessaryStubbingException
    }

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
     * Helper method to set the DoubleEntryService in the Account instance
     */
    private void setDoubleEntryService(Account account, DoubleEntryService service) {
        // No longer needed; remove this method
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
        
        // Mock the account with zero balance
        lenient().when(doubleEntryService.calculateBalance(eq(accountId))).thenReturn(BigDecimal.ZERO);
        lenient().when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

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