package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.TransactionRepository;

/**
 * Service for managing accounts.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final DoubleEntryService doubleEntryService;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository, 
                         DoubleEntryService doubleEntryService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.doubleEntryService = doubleEntryService;
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
    @Transactional
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
        // First verify the account exists
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        
        // Use the DoubleEntryService to calculate the balance from ledger entries
        return doubleEntryService.calculateBalance(accountId);
    }
    
    /**
     * Gets the maximum withdrawal amount for an account based on its type and current balance.
     *
     * @param accountId the ID of the account
     * @return the maximum withdrawal amount
     * @throws AccountNotFoundException if the account is not found
     */
    public BigDecimal getMaxWithdrawalAmount(UUID accountId) {
        // First get the account to check its type
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        
        // Get the current balance
        BigDecimal currentBalance = doubleEntryService.calculateBalance(accountId);
        
        // Determine maximum withdrawal based on account type
        if (account.getAccountType() instanceof AccountType.SystemAccount) {
            return new BigDecimal(Integer.MAX_VALUE);
        } else if (account.getAccountType().allowFullBalanceWithdrawal()) {
            return currentBalance;
        } else {
            return BigDecimal.ZERO;
        }
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
    
    /**
     * Get all accounts.
     * 
     * @return List of all accounts
     */
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }
} 