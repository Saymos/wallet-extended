package com.cubeia.wallet.repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.EntryType;
import com.cubeia.wallet.model.LedgerEntry;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.service.DoubleEntryService;

@DataJpaTest
@Import(DoubleEntryService.class)
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private DoubleEntryService doubleEntryService;
    
    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }
    
    /**
     * Creates a system credit entry to simulate a balance for an account
     */
    private void createSystemCredit(UUID accountId, BigDecimal amount) {
        // Create a system account if needed
        Account systemAccount = accountRepository.save(new Account(Currency.EUR, AccountType.SystemAccount.getInstance()));
        
        // Create a transaction to link the ledger entries
        Transaction transaction = new Transaction(
            systemAccount.getId(),
            accountId,
            amount,
            TransactionType.TRANSFER,  // Using TRANSFER type since there's no SYSTEM_CREDIT
            Currency.EUR
        );
        
        transaction = transactionRepository.save(transaction);
        
        // Create debit from system account
        LedgerEntry debitEntry = LedgerEntry.builder()
            .accountId(systemAccount.getId())
            .transactionId(transaction.getId())
            .entryType(EntryType.DEBIT)
            .amount(amount)
            .description("System credit")
            .build();
        
        // Create credit to target account
        LedgerEntry creditEntry = LedgerEntry.builder()
            .accountId(accountId)
            .transactionId(transaction.getId())
            .entryType(EntryType.CREDIT)
            .amount(amount)
            .description("System credit")
            .build();
        
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);
    }

    @Test
    void shouldSaveAccount() {
        // given
        Account account = new Account(Currency.EUR, AccountType.MainAccount.getInstance());

        // when
        Account savedAccount = accountRepository.save(account);
        // Inject the DoubleEntryService for balance calculation
        savedAccount.setDoubleEntryService(doubleEntryService);

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
        Account savedAccount = accountRepository.save(account);
        
        // Add balance by creating entries
        createSystemCredit(savedAccount.getId(), new BigDecimal("100.0000"));

        // when
        Optional<Account> foundAccountOpt = accountRepository.findById(savedAccount.getId());
        
        // Inject the DoubleEntryService for balance calculation
        foundAccountOpt.ifPresent(foundAccount -> foundAccount.setDoubleEntryService(doubleEntryService));
        
        // then
        assertTrue(foundAccountOpt.isPresent());
        Account foundAccount = foundAccountOpt.get();
        assertEquals(savedAccount.getId(), foundAccount.getId());
        
        // Verify the balance is calculated from ledger entries
        assertEquals(new BigDecimal("100.0000"), doubleEntryService.calculateBalance(foundAccount.getId()));
        assertEquals(new BigDecimal("100.0000"), foundAccount.getBalance());
        
        assertEquals(Currency.USD, foundAccount.getCurrency());
        assertEquals(AccountType.BonusAccount.getInstance(), foundAccount.getAccountType());
    }

    @Test
    void shouldFindAccountByIdWithLock() {
        // given
        Account account = new Account(Currency.GBP, AccountType.PendingAccount.getInstance());
        Account savedAccount = accountRepository.save(account);
        
        // Add balance by creating entries
        createSystemCredit(savedAccount.getId(), new BigDecimal("200.0000"));

        // when
        Optional<Account> foundAccountOpt = accountRepository.findByIdWithLock(savedAccount.getId());
        
        // Inject the DoubleEntryService for balance calculation
        foundAccountOpt.ifPresent(foundAccount -> foundAccount.setDoubleEntryService(doubleEntryService));
        
        // then
        assertTrue(foundAccountOpt.isPresent());
        Account foundAccount = foundAccountOpt.get();
        assertEquals(savedAccount.getId(), foundAccount.getId());
        
        // Verify the balance is calculated from ledger entries
        assertEquals(new BigDecimal("200.0000"), doubleEntryService.calculateBalance(foundAccount.getId()));
        assertEquals(new BigDecimal("200.0000"), foundAccount.getBalance());
        
        assertEquals(Currency.GBP, foundAccount.getCurrency());
        assertEquals(AccountType.PendingAccount.getInstance(), foundAccount.getAccountType());
    }

    @Test
    void shouldTrackAccountBalanceChanges() {
        // given
        Account account = new Account(Currency.CHF, AccountType.JackpotAccount.getInstance());
        Account savedAccount = accountRepository.save(account);
        
        // Initial credit to account
        createSystemCredit(savedAccount.getId(), new BigDecimal("300.0000"));

        // then - verify initial balance
        Optional<Account> initialAccountOpt = accountRepository.findById(savedAccount.getId());
        
        // Inject the DoubleEntryService for balance calculation
        initialAccountOpt.ifPresent(initialAcc -> initialAcc.setDoubleEntryService(doubleEntryService));
        
        assertTrue(initialAccountOpt.isPresent());
        Account initialAccount = initialAccountOpt.get();
        assertEquals(new BigDecimal("300.0000"), initialAccount.getBalance());
        
        // when - add another credit
        createSystemCredit(savedAccount.getId(), new BigDecimal("50.0000"));

        // then - verify updated balance
        Optional<Account> updatedAccountOpt = accountRepository.findById(savedAccount.getId());
        
        // Inject the DoubleEntryService for balance calculation
        updatedAccountOpt.ifPresent(updatedAcc -> updatedAcc.setDoubleEntryService(doubleEntryService));
        
        assertTrue(updatedAccountOpt.isPresent());
        Account updatedAccount = updatedAccountOpt.get();
        assertEquals(new BigDecimal("350.0000"), updatedAccount.getBalance());
    }

    @Test
    void shouldReturnEmptyOptionalForNonExistentAccount() {
        // when
        Optional<Account> nonExistentAccount = accountRepository.findById(UUID.randomUUID());

        // then
        assertFalse(nonExistentAccount.isPresent());
    }
} 