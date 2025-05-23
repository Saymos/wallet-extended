package com.cubeia.wallet.model;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;

import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubeia.wallet.repository.LedgerEntryRepository;
import com.cubeia.wallet.service.DoubleEntryService;

/**
 * Helper methods for testing with the wallet domain objects.
 * These methods provide utilities for setting up test objects without 
 * relying on database persistence.
 */
class TestHelpers {

    /**
     * Helper method to set account ID using reflection (for testing)
     * 
     * @param account The account to set the ID on
     * @param id The ID to set
     */
    public static void setAccountId(Account account, UUID id) {
        try {
            Field idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(account, id);
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException e) {
            throw new RuntimeException("Failed to set ID", e);
        }
    }
    
    /**
     * Creates a mock DoubleEntryService that will return the specified balance
     * for the given account. This method replaces setupAccountWithBalance
     * for the service-based balance calculation approach.
     * 
     * @param account The account to set up the mock for
     * @param balance The balance value to return
     * @return The mocked DoubleEntryService
     */
    public static DoubleEntryService createMockServiceForAccount(Account account, BigDecimal balance) {
        // Ensure the account has an ID
        if (account.getId() == null) {
            setAccountId(account, UUID.randomUUID());
        }
        
        // Create and return a mock service that returns the balance for this account
        DoubleEntryService mockService = Mockito.mock(DoubleEntryService.class);
        Mockito.when(mockService.calculateBalance(account.getId())).thenReturn(balance);
        Mockito.when(mockService.calculateBalanceByCurrency(account.getId(), account.getCurrency()))
            .thenReturn(balance);
        
        return mockService;
    }
    
    /**
     * Creates a mock LedgerEntryRepository that will return the specified balance
     * for a given account ID.
     * 
     * @param accountId The account ID to set up the mock for
     * @param balance The balance value to return
     * @return The mocked repository
     */
    public static LedgerEntryRepository createMockLedgerRepository(UUID accountId, BigDecimal balance) {
        LedgerEntryRepository mockRepo = Mockito.mock(LedgerEntryRepository.class);
        Mockito.when(mockRepo.calculateBalance(accountId)).thenReturn(balance);
        return mockRepo;
    }
    
    /**
     * Create a DoubleEntryService that uses a mocked repository to return
     * the specified balance values for the given accounts.
     * 
     * @param accounts The accounts to configure in the service
     * @param balances The corresponding balances for each account
     * @return A configured DoubleEntryService with mocked repository
     */
    public static DoubleEntryService createMockDoubleEntryService(Account[] accounts, BigDecimal[] balances) {
        if (accounts.length != balances.length) {
            throw new IllegalArgumentException("Accounts and balances arrays must be the same length");
        }
        
        LedgerEntryRepository mockRepo = Mockito.mock(LedgerEntryRepository.class);
        
        // Configure the mock repository to return the specified balances
        for (int i = 0; i < accounts.length; i++) {
            UUID accountId = accounts[i].getId();
            if (accountId == null) {
                accountId = UUID.randomUUID();
                setAccountId(accounts[i], accountId);
            }
            
            Mockito.when(mockRepo.calculateBalance(accountId)).thenReturn(balances[i]);
        }
        
        return new DoubleEntryService(mockRepo, null);
    }

    /**
     * Creates a mock LedgerEntryRepository that returns a specific balance for testing.
     *
     * @param balance The balance to report for any account
     * @return A mocked repository
     */
    public static LedgerEntryRepository createMockLedgerEntryRepository(BigDecimal balance) {
        LedgerEntryRepository mockRepo = mock(LedgerEntryRepository.class);
        when(mockRepo.calculateBalance(org.mockito.ArgumentMatchers.any(UUID.class)))
            .thenReturn(balance);
        when(mockRepo.calculateBalanceByCurrency(
            org.mockito.ArgumentMatchers.any(UUID.class), 
            org.mockito.ArgumentMatchers.any(Currency.class)))
            .thenReturn(balance);
        return mockRepo;
    }
    
    /**
     * Creates a mock DoubleEntryService with mocked repository for testing.
     *
     * @param balance The balance to report for any account 
     * @return A DoubleEntryService with mocked repository
     */
    public static DoubleEntryService createMockDoubleEntryServiceWithRepo(BigDecimal balance) {
        LedgerEntryRepository mockRepo = createMockLedgerEntryRepository(balance);
        return new DoubleEntryService(mockRepo, null);
    }
    
    /**
     * Creates a mock DoubleEntryService that returns the specified balance.
     * This is useful for tests that require a mocked balance calculation.
     *
     * @param accountId The account ID to expect
     * @param balance The balance to return
     * @return A mocked DoubleEntryService
     */
    public static DoubleEntryService createMockDoubleEntryService(UUID accountId, BigDecimal balance) {
        DoubleEntryService mockService = mock(DoubleEntryService.class);
        when(mockService.calculateBalance(accountId)).thenReturn(balance);
        when(mockService.calculateBalanceByCurrency(accountId, Currency.EUR)).thenReturn(balance);
        
        return mockService;
    }
} 