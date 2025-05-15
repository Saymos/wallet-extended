package com.cubeia.wallet.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.EntryType;
import com.cubeia.wallet.model.LedgerEntry;

/**
 * Integration tests for the LedgerEntryRepository.
 * <p>
 * These tests verify that the repository methods correctly interact with the database
 * to store and retrieve ledger entries.
 * </p>
 */
@DataJpaTest
public class LedgerEntryRepositoryTest {

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    
    private UUID accountId;
    private UUID transactionId1;
    private UUID transactionId2;
    
    @BeforeEach
    public void setUp() {
        // Clear repository before each test
        ledgerEntryRepository.deleteAll();
        
        accountId = UUID.randomUUID();
        transactionId1 = UUID.randomUUID();
        transactionId2 = UUID.randomUUID();
        
        // Create and save test ledger entries
        
        // Transaction 1: Credit 100
        LedgerEntry creditEntry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId1)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("100.00"))
                .description("Test credit entry")
                .currency(Currency.EUR)
                .build();
        
        // Transaction 1: Debit from another account (not relevant for this test)
        LedgerEntry otherAccountEntry = LedgerEntry.builder()
                .accountId(UUID.randomUUID())
                .transactionId(transactionId1)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("100.00"))
                .description("Test debit entry for another account")
                .currency(Currency.EUR)
                .build();
        
        // Transaction 2: Debit 30
        LedgerEntry debitEntry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId2)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("30.00"))
                .description("Test debit entry")
                .currency(Currency.EUR)
                .build();
        
        // Transaction 2: Credit to another account (not relevant for this test)
        LedgerEntry otherAccountEntry2 = LedgerEntry.builder()
                .accountId(UUID.randomUUID())
                .transactionId(transactionId2)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("30.00"))
                .description("Test credit entry for another account")
                .currency(Currency.EUR)
                .build();
        
        // Save all entries
        ledgerEntryRepository.saveAll(List.of(creditEntry, otherAccountEntry, debitEntry, otherAccountEntry2));
    }
    
    @Test
    public void testFindByAccountIdOrderByTimestampDesc() {
        // Test with pagination
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<LedgerEntry> entriesPage = ledgerEntryRepository.findByAccountIdOrderByTimestampDesc(accountId, pageRequest);
        
        assertNotNull(entriesPage);
        assertEquals(2, entriesPage.getContent().size(), "Should find 2 entries for the account");
        
        // Test without pagination
        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdOrderByTimestampDesc(accountId);
        
        assertNotNull(entries);
        assertEquals(2, entries.size(), "Should find 2 entries for the account");
    }
    
    @Test
    public void testFindByTransactionId() {
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(transactionId1);
        
        assertNotNull(entries);
        assertEquals(2, entries.size(), "Should find 2 entries for the transaction");
        
        // Verify that one of these entries is for our test account
        boolean foundForAccount = entries.stream()
                .anyMatch(entry -> entry.getAccountId().equals(accountId));
        
        assertTrue(foundForAccount, "Should find an entry for the test account");
    }
    
    @Test
    public void testSumByAccountIdAndType() {
        BigDecimal creditSum = ledgerEntryRepository.sumByAccountIdAndType(accountId, EntryType.CREDIT);
        BigDecimal debitSum = ledgerEntryRepository.sumByAccountIdAndType(accountId, EntryType.DEBIT);
        
        assertEquals(new BigDecimal("100.00"), creditSum.setScale(2), "Credit sum should be 100.00");
        assertEquals(new BigDecimal("30.00"), debitSum.setScale(2), "Debit sum should be 30.00");
        
        // Test for non-existent account (should return zero)
        BigDecimal nonExistentResult = ledgerEntryRepository.sumByAccountIdAndType(UUID.randomUUID(), EntryType.CREDIT);
        assertEquals(BigDecimal.ZERO, nonExistentResult, "Should return zero for non-existent account");
    }
    
    @Test
    public void testCalculateBalance() {
        BigDecimal balance = ledgerEntryRepository.calculateBalance(accountId);
        
        assertEquals(new BigDecimal("70.00"), balance.setScale(2), 
                "Balance should be 70.00 (CREDIT 100 - DEBIT 30)");
        
        // Test for non-existent account (should return zero)
        BigDecimal nonExistentBalance = ledgerEntryRepository.calculateBalance(UUID.randomUUID());
        assertEquals(BigDecimal.ZERO, nonExistentBalance, "Should return zero for non-existent account");
    }
    
    @Test
    public void testFindByAccountIdAndEntryTypeOrderByTimestampDesc() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        
        Page<LedgerEntry> creditEntries = ledgerEntryRepository
                .findByAccountIdAndEntryTypeOrderByTimestampDesc(accountId, EntryType.CREDIT, pageRequest);
        
        Page<LedgerEntry> debitEntries = ledgerEntryRepository
                .findByAccountIdAndEntryTypeOrderByTimestampDesc(accountId, EntryType.DEBIT, pageRequest);
        
        assertEquals(1, creditEntries.getContent().size(), "Should find 1 credit entry");
        assertEquals(EntryType.CREDIT, creditEntries.getContent().get(0).getEntryType(), 
                "Entry should be of type CREDIT");
        
        assertEquals(1, debitEntries.getContent().size(), "Should find 1 debit entry");
        assertEquals(EntryType.DEBIT, debitEntries.getContent().get(0).getEntryType(), 
                "Entry should be of type DEBIT");
    }
} 