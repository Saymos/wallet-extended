package com.cubeia.wallet.repository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.cubeia.wallet.model.Account;

@DataJpaTest
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void shouldSaveAccount() {
        // given
        Account account = new Account();
        account.setBalance(BigDecimal.ZERO);

        // when
        Account savedAccount = accountRepository.save(account);

        // then
        assertThat(savedAccount.getId()).isNotNull();
        assertThat(savedAccount.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldFindAccountById() {
        // given
        Account account = new Account();
        account.setBalance(new BigDecimal("100.0000"));
        
        Account savedAccount = accountRepository.save(account);

        // when
        Optional<Account> foundAccount = accountRepository.findById(savedAccount.getId());

        // then
        assertThat(foundAccount).isPresent();
        assertThat(foundAccount.get().getId()).isEqualTo(savedAccount.getId());
        assertThat(foundAccount.get().getBalance()).isEqualByComparingTo(new BigDecimal("100.0000"));
    }

    @Test
    void shouldFindAccountByIdWithLock() {
        // given
        Account account = new Account();
        account.setBalance(new BigDecimal("200.0000"));
        
        Account savedAccount = accountRepository.save(account);

        // when
        Optional<Account> foundAccount = accountRepository.findByIdWithLock(savedAccount.getId());

        // then
        assertThat(foundAccount).isPresent();
        assertThat(foundAccount.get().getId()).isEqualTo(savedAccount.getId());
        assertThat(foundAccount.get().getBalance()).isEqualByComparingTo(new BigDecimal("200.0000"));
    }

    @Test
    void shouldUpdateAccountBalance() {
        // given
        Account account = new Account();
        account.setBalance(new BigDecimal("300.0000"));
        
        Account savedAccount = accountRepository.save(account);

        // when
        savedAccount.setBalance(new BigDecimal("350.0000"));
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