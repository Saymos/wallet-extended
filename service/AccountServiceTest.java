package com.cubeia.wallet.service;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void createAccount_ShouldCreateAccountWithZeroBalance() {
        // Arrange
        Account account = new Account();
        account.setId(1L);
        account.setBalance(BigDecimal.ZERO);
        
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        // Act
        Account result = accountService.createAccount();

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(BigDecimal.ZERO, result.getBalance());
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void getBalance_ShouldReturnCorrectBalance() {
        // Arrange
        Long accountId = 1L;
        BigDecimal expectedBalance = BigDecimal.valueOf(100.0);
        
        Account account = new Account();
        account.setId(accountId);
        account.setBalance(expectedBalance);
        
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
} 