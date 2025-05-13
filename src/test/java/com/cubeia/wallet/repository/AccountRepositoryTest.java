package com.cubeia.wallet.repository;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        assertThat(savedAccount.getId()).isNotNull();
        assertThat(savedAccount.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(savedAccount.getCurrency()).isEqualTo(Currency.EUR);
        assertThat(savedAccount.getAccountType()).isEqualTo(AccountType.MainAccount.getInstance());
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
        assertThat(foundAccount).isPresent();
        assertThat(foundAccount.get().getId()).isEqualTo(savedAccount.getId());
        assertThat(foundAccount.get().getBalance()).isEqualByComparingTo(new BigDecimal("100.0000"));
        assertThat(foundAccount.get().getCurrency()).isEqualTo(Currency.USD);
        assertThat(foundAccount.get().getAccountType()).isEqualTo(AccountType.BonusAccount.getInstance());
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
        assertThat(foundAccount).isPresent();
        assertThat(foundAccount.get().getId()).isEqualTo(savedAccount.getId());
        assertThat(foundAccount.get().getBalance()).isEqualByComparingTo(new BigDecimal("200.0000"));
        assertThat(foundAccount.get().getCurrency()).isEqualTo(Currency.GBP);
        assertThat(foundAccount.get().getAccountType()).isEqualTo(AccountType.PendingAccount.getInstance());
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
        assertThat(updatedAccount).isPresent();
        assertThat(updatedAccount.get().getBalance()).isEqualByComparingTo(new BigDecimal("350.0000"));
    }

    @Test
    void shouldReturnEmptyOptionalForNonExistentAccount() {
        // when
        Optional<Account> nonExistentAccount = accountRepository.findById(999L);

        // then
        assertThat(nonExistentAccount).isEmpty();
    }
} 