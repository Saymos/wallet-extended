package com.cubeia.wallet.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Tests for the LedgerEntry entity to ensure it functions correctly
 * with JPA and maintains proper immutability and validation.
 */
@DataJpaTest
class LedgerEntryTest {

    @Autowired
    private TestEntityManager entityManager;

    private UUID accountId;
    private UUID transactionId;
    private final BigDecimal amount = new BigDecimal("100.00");
    private final String description = "Test ledger entry";
    private final Currency currency = Currency.EUR;

    @BeforeEach
    public void setUp() {
        accountId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
    }

    @Test
    public void testPersistenceWithJPA() {
        // Create a ledger entry
        LedgerEntry debitEntry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .description(description)
                .currency(currency)
                .build();

        // Persist the entry
        LedgerEntry savedEntry = entityManager.persistFlushFind(debitEntry);

        // Verify it was correctly persisted
        assertNotNull(savedEntry.getId(), "ID should be generated");
        assertEquals(accountId, savedEntry.getAccountId(), "Account ID should match");
        assertEquals(transactionId, savedEntry.getTransactionId(), "Transaction ID should match");
        assertEquals(EntryType.DEBIT, savedEntry.getEntryType(), "Entry type should match");
        assertEquals(0, amount.compareTo(savedEntry.getAmount()), "Amount should match");
        assertEquals(description, savedEntry.getDescription(), "Description should match");
        assertEquals(currency, savedEntry.getCurrency(), "Currency should match");
        assertNotNull(savedEntry.getTimestamp(), "Timestamp should be generated");
    }

    @Test
    public void testEntryTypesWithSignedAmount() {
        // Create a debit entry
        LedgerEntry debitEntry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .description(description)
                .currency(currency)
                .build();

        // Create a credit entry
        LedgerEntry creditEntry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .description(description)
                .currency(currency)
                .build();

        // Verify signed amounts
        assertEquals(amount.negate(), debitEntry.getSignedAmount(), "Debit should have negative signed amount");
        assertEquals(amount, creditEntry.getSignedAmount(), "Credit should have positive signed amount");
    }
    
    @Test
    public void testNullFieldValidation() {
        // Account ID
        IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class, () -> 
            LedgerEntry.builder()
                .accountId(null)
                .transactionId(transactionId)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .description(description)
                .currency(currency)
                .build(),
            "Should validate accountId is not null");
        assertNotNull(exception1.getMessage(), "Exception message should not be null");

        // Transaction ID
        IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class, () -> 
            LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(null)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .description(description)
                .currency(currency)
                .build(),
            "Should validate transactionId is not null");
        assertNotNull(exception2.getMessage(), "Exception message should not be null");

        // Entry Type
        IllegalArgumentException exception3 = assertThrows(IllegalArgumentException.class, () -> 
            LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .entryType(null)
                .amount(amount)
                .description(description)
                .currency(currency)
                .build(),
            "Should validate entryType is not null");
        assertNotNull(exception3.getMessage(), "Exception message should not be null");

        // Amount
        IllegalArgumentException exception4 = assertThrows(IllegalArgumentException.class, () -> 
            LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .entryType(EntryType.DEBIT)
                .amount(null)
                .description(description)
                .currency(currency)
                .build(),
            "Should validate amount is not null");
        assertNotNull(exception4.getMessage(), "Exception message should not be null");
            
        // Currency
        IllegalArgumentException exception5 = assertThrows(IllegalArgumentException.class, () -> 
            LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .description(description)
                .currency(null)
                .build(),
            "Should validate currency is not null");
        assertNotNull(exception5.getMessage(), "Exception message should not be null");
    }
    
    @Test
    public void testAmountValidation() {
        // Zero amount
        IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class, () -> 
            LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .entryType(EntryType.DEBIT)
                .amount(BigDecimal.ZERO)
                .description(description)
                .currency(currency)
                .build(),
            "Should validate amount is positive");
        assertNotNull(exception1.getMessage(), "Exception message should not be null");

        // Zero amount after abs()
        IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class, () -> 
            LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("0.00"))
                .description(description)
                .currency(currency)
                .build(),
            "Should validate amount is positive");
        assertNotNull(exception2.getMessage(), "Exception message should not be null");
    }
    
    @Test
    public void testDescriptionIsOptional() {
        // Create entry without description
        LedgerEntry entry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .currency(currency)
                .build();
                
        // Persist and verify
        LedgerEntry savedEntry = entityManager.persistFlushFind(entry);
        assertNotNull(savedEntry.getId(), "Entry should be persisted without description");
    }
    
    @Test
    public void testImmutability() {
        // Create and persist an entry
        LedgerEntry entry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .description(description)
                .currency(currency)
                .build();
                
        LedgerEntry savedEntry = entityManager.persistFlushFind(entry);
        
        // Verify that there are no setters to modify the entity
        LocalDateTime originalTime = savedEntry.getTimestamp();
        
        // Wait a moment to ensure a new timestamp would be different
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Refresh from database and verify unchanged
        entityManager.refresh(savedEntry);
        assertEquals(originalTime, savedEntry.getTimestamp(), 
            "Timestamp should not change, entity should be immutable");
    }
    
    @Test
    public void testAbsoluteAmountStorage() {
        // Create with negative amount that's converted to positive for storage
        LedgerEntry entry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("-50.00"))
                .description(description)
                .currency(currency)
                .build();
                
        // Verify that the amount is stored as absolute value
        assertEquals(new BigDecimal("50.00").setScale(2), entry.getAmount().setScale(2), 
            "Amount should be stored as absolute value");
        assertEquals(new BigDecimal("-50.00").setScale(2), entry.getSignedAmount().setScale(2), 
            "Signed amount should be negative for DEBIT");
    }
} 