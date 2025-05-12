package com.cubeia.wallet.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.repository.AccountRepository;

/**
 * Service for managing accounts.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Creates a new account with zero balance.
     *
     * @return the created account
     */
    public Account createAccount() {
        Account account = new Account();
        account.setBalance(BigDecimal.ZERO);
        
        return accountRepository.save(account);
    }

    /**
     * Gets the balance of an account.
     *
     * @param accountId the ID of the account
     * @return the balance of the account
     * @throws AccountNotFoundException if the account is not found
     */
    public BigDecimal getBalance(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        
        return account.getBalance();
    }
} 