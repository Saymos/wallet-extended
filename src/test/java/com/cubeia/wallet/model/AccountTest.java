package com.cubeia.wallet.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.cubeia.wallet.repository.AccountRepository;

/**
 * Tests for verifying JPA compatibility with Account entity.
 * These tests ensure Account objects can be properly persisted and retrieved
 * through JPA mechanisms, especially focusing on fields with 'final' modifiers.
 */
@DataJpaTest
@ActiveProfiles("test")
public class AccountTest {

    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private AccountRepository accountRepository;
    
    /**
     * Test that an Account can be persisted and then retrieved with all properties intact.
     */
    @Test
    public void testPersistAndLoad() {
        // Create an account
        Account account = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        
        // Persist it
        entityManager.persistAndFlush(account);
        
        // Clear the persistence context to force a database read
        entityManager.clear();
        
        // Retrieve the account from database
        Account retrievedAccount = entityManager.find(Account.class, account.getId());
        
        // Assert all properties are maintained correctly
        assertNotNull(retrievedAccount);
        assertEquals(account.getId(), retrievedAccount.getId());
        // Compare BigDecimals properly
        assertTrue(BigDecimal.ZERO.compareTo(retrievedAccount.getBalance()) == 0, 
                "Balance should be zero");
        assertEquals(Currency.EUR, retrievedAccount.getCurrency());
        assertEquals(AccountType.MainAccount.getInstance(), retrievedAccount.getAccountType());
    }
    
    /**
     * Test creating an account with all properties, persisting it, and retrieving it.
     */
    @Test
    public void testCreatePersistWithAllProperties() {
        // Create account
        Account account = new Account(Currency.USD, AccountType.BonusAccount.getInstance());
        
        // Save using repository
        Account savedAccount = accountRepository.save(account);
        
        // Flush changes to the database
        accountRepository.flush();
        
        // Clear JPA's first-level cache
        entityManager.clear();
        
        // Retrieve using repository
        Optional<Account> retrievedAccountOpt = accountRepository.findById(account.getId());
        
        // Verify the account was saved and can be retrieved
        assertTrue(retrievedAccountOpt.isPresent(), "Account should be retrievable from repository");
        
        Account retrievedAccount = retrievedAccountOpt.get();
        assertEquals(Currency.USD, retrievedAccount.getCurrency());
        assertEquals(AccountType.BonusAccount.getInstance(), retrievedAccount.getAccountType());
    }
    
    /**
     * Test that an account with multiple changes can still be properly persisted and retrieved.
     */
    @Test
    public void testMultipleStateChanges() {
        // Create account
        Account account = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        
        // Persist it
        entityManager.persistAndFlush(account);
        UUID id = account.getId();
        
        // Load it fresh
        entityManager.clear();
        Account savedAccount = entityManager.find(Account.class, id);
        
        // Update balance using internal method (this tests if JPA can handle updates
        // to fields that were originally marked as final)
        BigDecimal newBalance = new BigDecimal("100.00");
        savedAccount.updateBalance(newBalance);
        
        // Persist the change
        entityManager.persistAndFlush(savedAccount);
        entityManager.clear();
        
        // Load again and verify updates were persisted
        Account updatedAccount = entityManager.find(Account.class, id);
        assertTrue(newBalance.compareTo(updatedAccount.getBalance()) == 0, 
                "Balance should be 100.00");
        assertEquals(Currency.EUR, updatedAccount.getCurrency());
    }
    
    /**
     * Test creating account using the repository layer directly.
     * Note that in this simplified test, we focus only on basic JPA operations
     * and not on the custom repository methods that require transactions.
     */
    @Test
    public void testRepositoryCreateAndFind() {
        // Create account using repository
        Account account = new Account(Currency.EUR, AccountType.JackpotAccount.getInstance());
        
        // Save the account directly through the EntityManager to ensure it's stored
        entityManager.persist(account);
        entityManager.flush();
        
        // Clear persistence context
        entityManager.clear();
        
        // Use findById to ensure it's stored and retrievable
        UUID id = account.getId();
        Optional<Account> retrievedAccountOpt = accountRepository.findById(id);
        
        assertTrue(retrievedAccountOpt.isPresent(), "Account should be retrievable from repository");
        
        Account retrievedAccount = retrievedAccountOpt.get();
        assertEquals(Currency.EUR, retrievedAccount.getCurrency());
        assertEquals(AccountType.JackpotAccount.getInstance(), retrievedAccount.getAccountType());
    }
} 