package com.cubeia.wallet.repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;

/**
 * Tests for reference uniqueness and case insensitive lookup.
 * This is a standalone test that uses the Spring context but doesn't depend on
 * other test classes that might have compatibility issues.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ReferenceIntegrityTest {

    @Autowired
    private TransactionRepository transactionRepository;
    
    @Test
    public void testReferenceUniqueness() {
        // Create two transactions with the same reference
        UUID fromId1 = UUID.randomUUID();
        UUID toId1 = UUID.randomUUID();
        UUID fromId2 = UUID.randomUUID();
        UUID toId2 = UUID.randomUUID();
        String sameReference = "DUPLICATE-REF-" + UUID.randomUUID().toString();
        
        Transaction tx1 = new Transaction(
            fromId1, toId1, 
            new BigDecimal("100.00"), 
            TransactionType.TRANSFER, 
            Currency.EUR, 
            sameReference
        );
        
        Transaction tx2 = new Transaction(
            fromId2, toId2, 
            new BigDecimal("200.00"), 
            TransactionType.TRANSFER, 
            Currency.USD, 
            sameReference
        );
        
        // Save the first transaction
        transactionRepository.save(tx1);
        
        // Try to save the second transaction - should fail with constraint violation
        transactionRepository.save(tx2);
        assertThrows(DataIntegrityViolationException.class, () -> {
            transactionRepository.flush();
        });
    }
    
    @Test
    public void testCaseInsensitiveReferenceLookup() {
        // Create a transaction with a mixed-case reference
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        String mixedCaseRef = "Test-MIXED-Case-" + UUID.randomUUID().toString();
        
        Transaction tx = new Transaction(
            fromId, toId, 
            new BigDecimal("150.00"), 
            TransactionType.TRANSFER, 
            Currency.EUR, 
            mixedCaseRef
        );
        
        transactionRepository.save(tx);
        transactionRepository.flush();
        
        // Look up with lowercase
        Optional<Transaction> found1 = transactionRepository.findByReferenceIgnoreCase(
            mixedCaseRef.toLowerCase());
        
        // Look up with uppercase
        Optional<Transaction> found2 = transactionRepository.findByReferenceIgnoreCase(
            mixedCaseRef.toUpperCase());
        
        // Verify both lookups found the transaction
        assertTrue(found1.isPresent(), "Should find transaction with lowercase reference");
        assertTrue(found2.isPresent(), "Should find transaction with uppercase reference");
        
        // Verify it's the same transaction 
        assertEquals(tx.getId(), found1.get().getId());
        assertEquals(tx.getId(), found2.get().getId());
        
        // Verify original casing is preserved
        assertEquals(mixedCaseRef, found1.get().getReference());
        assertEquals(mixedCaseRef, found2.get().getReference());
    }
} 