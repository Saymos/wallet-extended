package com.cubeia.wallet.model;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;

import org.mockito.Mockito;

import com.cubeia.wallet.repository.LedgerEntryRepository;
import com.cubeia.wallet.service.DoubleEntryService;

/**
 * Helper methods for testing with the wallet domain objects.
 * These methods provide utilities for setting up test objects without 
 * relying on database persistence.
 */
public class TestHelpers {

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
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID", e);
        }
    }
    
    /**
     * Helper method to setup account with a mocked DoubleEntryService
     * that will return the specified balance.
     * 
     * @param account The account to setup
     * @param balance The balance that the mocked service should return
     * @return The mocked DoubleEntryService for further customization if needed
     */
    public static DoubleEntryService setupAccountWithBalance(Account account, BigDecimal balance) {
        // Ensure the account has an ID
        if (account.getId() == null) {
            setAccountId(account, UUID.randomUUID());
        }
        
        // Create a mock DoubleEntryService
        DoubleEntryService mockService = Mockito.mock(DoubleEntryService.class);
        
        // Setup the mock to return the specified balance for this account
        Mockito.when(mockService.calculateBalance(account.getId())).thenReturn(balance);
        
        // Connect the mock service to the account
        account.setDoubleEntryService(mockService);
        
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
} 