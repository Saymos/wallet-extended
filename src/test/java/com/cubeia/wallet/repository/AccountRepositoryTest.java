package com.cubeia.wallet.repository;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;

@DataJpaTest
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;
    
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
    void shouldSaveAccount() {
        // given
        Account account = new Account(Currency.EUR, AccountType.MainAccount.getInstance());

        // when
        Account savedAccount = accountRepository.save(account);

        // then
        assertNotNull(savedAccount.getId());
        assertEquals(BigDecimal.ZERO, savedAccount.getBalance());
        assertEquals(Currency.EUR, savedAccount.getCurrency());
        assertEquals(AccountType.MainAccount.getInstance(), savedAccount.getAccountType());
    }

    @Test
    void shouldFindAccountById() {
        // given
        Account account = new Account(Currency.USD, AccountType.BonusAccount.getInstance());
        setAccountBalance(account, new BigDecimal("100.0000"));
        
        Account savedAccount = accountRepository.save(account);

        // when
        Optional<Account> foundAccount = accountRepository.findById(savedAccount.getId());

        // then
        assertTrue(foundAccount.isPresent());
        assertEquals(savedAccount.getId(), foundAccount.get().getId());
        assertEquals(new BigDecimal("100.0000"), foundAccount.get().getBalance());
        assertEquals(Currency.USD, foundAccount.get().getCurrency());
        assertEquals(AccountType.BonusAccount.getInstance(), foundAccount.get().getAccountType());
    }

    @Test
    void shouldFindAccountByIdWithLock() {
        // given
        Account account = new Account(Currency.GBP, AccountType.PendingAccount.getInstance());
        setAccountBalance(account, new BigDecimal("200.0000"));
        
        Account savedAccount = accountRepository.save(account);

        // when
        Optional<Account> foundAccount = accountRepository.findByIdWithLock(savedAccount.getId());

        // then
        assertTrue(foundAccount.isPresent());
        assertEquals(savedAccount.getId(), foundAccount.get().getId());
        assertEquals(new BigDecimal("200.0000"), foundAccount.get().getBalance());
        assertEquals(Currency.GBP, foundAccount.get().getCurrency());
        assertEquals(AccountType.PendingAccount.getInstance(), foundAccount.get().getAccountType());
    }

    @Test
    void shouldUpdateAccountBalance() {
        // given
        Account account = new Account(Currency.CHF, AccountType.JackpotAccount.getInstance());
        setAccountBalance(account, new BigDecimal("300.0000"));
        
        Account savedAccount = accountRepository.save(account);

        // when - update using reflection since we removed setters
        setAccountBalance(savedAccount, new BigDecimal("350.0000"));
        accountRepository.save(savedAccount);

        // then
        Optional<Account> updatedAccount = accountRepository.findById(savedAccount.getId());
        assertTrue(updatedAccount.isPresent());
        assertEquals(new BigDecimal("350.0000"), updatedAccount.get().getBalance());
    }

    @Test
    void shouldReturnEmptyOptionalForNonExistentAccount() {
        // when
        Optional<Account> nonExistentAccount = accountRepository.findById(UUID.randomUUID());

        // then
        assertFalse(nonExistentAccount.isPresent());
    }
} 