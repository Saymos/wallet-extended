package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
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
     * Creates a new account with default currency (EUR) and type (MAIN).
     *
     * @return the created account
     */
    public Account createAccount() {
        // Create a new account with default values
        Account account = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        return accountRepository.save(account);
    }
    
    /**
     * Creates a new account with the specified currency and type.
     *
     * @param currency the currency for the account
     * @param accountType the type of account
     * @return the created account
     */
    public Account createAccount(Currency currency, AccountType accountType) {
        Account account = new Account(currency, accountType);
        return accountRepository.save(account);
    }

    /**
     * Gets the balance of an account.
     *
     * @param accountId the ID of the account
     * @return the balance of the account
     * @throws AccountNotFoundException if the account is not found
     */
    public BigDecimal getBalance(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        
        return account.getBalance();
    }
    
    /**
     * Gets an account by its ID.
     *
     * @param accountId the ID of the account
     * @return the account
     * @throws AccountNotFoundException if the account is not found
     */
    public Account getAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
} 