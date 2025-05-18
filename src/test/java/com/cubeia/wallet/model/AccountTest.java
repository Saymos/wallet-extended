package com.cubeia.wallet.model;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.service.DoubleEntryService;

/**
 * Tests for verifying JPA compatibility with Account entity.
 * These tests ensure Account objects can be properly persisted and retrieved
 * through JPA mechanisms, especially focusing on fields with 'final' modifiers.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(AccountTest.TestConfig.class)
class AccountTest {

    /**
     * Test configuration that provides a mock DoubleEntryService
     * to avoid database dependencies in tests
     */
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public DoubleEntryService doubleEntryService() {
            DoubleEntryService mockService = mock(DoubleEntryService.class);
            when(mockService.calculateBalance(any(UUID.class))).thenReturn(BigDecimal.ZERO);
            return mockService;
        }
    }

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
        assertEquals(Currency.EUR, retrievedAccount.getCurrency());
        assertEquals(AccountType.MainAccount.getInstance(), retrievedAccount.getAccountType());
        
        // We can't directly test the balance since it's now calculated through a service
    }
    
    /**
     * Test creating an account with all properties, persisting it, and retrieving it.
     */
    @Test
    public void testCreatePersistWithAllProperties() {
        // Create account
        Account account = new Account(Currency.USD, AccountType.BonusAccount.getInstance());
        
        // Save using repository
        accountRepository.save(account);
        
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
     * Test creating account using the repository layer directly.
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