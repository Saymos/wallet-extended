package com.cubeia.wallet.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DataJpaTest
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;
    
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldSaveTransaction() {
        // given
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        Transaction transaction = new Transaction(
            fromAccountId, toAccountId, new BigDecimal("100.0000"), 
            TransactionType.TRANSFER, Currency.EUR
        );

        // when
        transaction = transactionRepository.save(transaction);
        // Flush to ensure the entity is persisted
        entityManager.flush();

        // then
        assertNotNull(transaction);
        assertNotNull(transaction.getId());
        assertEquals(fromAccountId, transaction.getFromAccountId());
        assertEquals(toAccountId, transaction.getToAccountId());
        assertEquals(new BigDecimal("100.0000"), transaction.getAmount());
        assertEquals(TransactionType.TRANSFER, transaction.getTransactionType());
        assertEquals(Currency.EUR, transaction.getCurrency());
        assertNotNull(transaction.getTimestamp());
    }

    @Test
    void shouldFindTransactionById() {
        // given
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        Transaction transaction = new Transaction(
            fromAccountId, toAccountId, new BigDecimal("200.0000"), 
            TransactionType.DEPOSIT, Currency.USD
        );
        
        Transaction savedTransaction = transactionRepository.save(transaction);
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<Transaction> foundTransaction = transactionRepository.findById(savedTransaction.getId());

        // then
        assertTrue(foundTransaction.isPresent());
        assertEquals(savedTransaction.getId(), foundTransaction.get().getId());
        assertEquals(fromAccountId, foundTransaction.get().getFromAccountId());
        assertEquals(toAccountId, foundTransaction.get().getToAccountId());
        assertEquals(new BigDecimal("200.0000"), foundTransaction.get().getAmount());
        assertEquals(TransactionType.DEPOSIT, foundTransaction.get().getTransactionType());
        assertEquals(Currency.USD, foundTransaction.get().getCurrency());
    }

    @Test
    void shouldFindTransactionsByAccountId() {
        // given
        UUID account1Id = UUID.randomUUID();
        UUID account2Id = UUID.randomUUID();
        UUID account3Id = UUID.randomUUID();
        UUID account4Id = UUID.randomUUID();
        
        Transaction transaction1 = new Transaction(
            account1Id, account2Id, new BigDecimal("300.0000"), 
            TransactionType.WITHDRAWAL, Currency.GBP
        );
        
        Transaction transaction2 = new Transaction(
            account3Id, account1Id, new BigDecimal("400.0000"), 
            TransactionType.TRANSFER, Currency.GBP
        );
        
        Transaction transaction3 = new Transaction(
            account4Id, account2Id, new BigDecimal("500.0000"), 
            TransactionType.GAME_BET, Currency.EUR
        );
        
        transactionRepository.saveAll(List.of(transaction1, transaction2, transaction3));
        entityManager.flush();
        entityManager.clear();

        // when
        List<Transaction> accountTransactions = transactionRepository.findByAccountId(account1Id);

        // then
        assertEquals(2, accountTransactions.size());
        
        // Verify first transaction is in the result (account1 sending to account2)
        boolean hasOutgoingTransaction = false;
        boolean hasIncomingTransaction = false;
        
        for (Transaction t : accountTransactions) {
            if (t.getFromAccountId().equals(account1Id) && t.getToAccountId().equals(account2Id)) {
                hasOutgoingTransaction = true;
            }
            if (t.getFromAccountId().equals(account3Id) && t.getToAccountId().equals(account1Id)) {
                hasIncomingTransaction = true;
            }
        }
        
        assertTrue(hasOutgoingTransaction, "Should contain outgoing transaction");
        assertTrue(hasIncomingTransaction, "Should contain incoming transaction");
    }

    @Test
    void shouldReturnEmptyListForAccountWithNoTransactions() {
        // when
        List<Transaction> accountTransactions = transactionRepository.findByAccountId(UUID.randomUUID());

        // then
        assertTrue(accountTransactions.isEmpty());
    }
    
    @Test
    void shouldFindTransactionByReference() {
        // given
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        String referenceId = "TEST-REF-" + UUID.randomUUID().toString();
        
        Transaction transaction = new Transaction(
            fromAccountId, toAccountId, new BigDecimal("100.0000"), 
            TransactionType.TRANSFER, Currency.EUR, referenceId
        );
        
        transactionRepository.save(transaction);
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<Transaction> foundTransaction = transactionRepository.findByReference(referenceId);

        // then
        assertTrue(foundTransaction.isPresent());
        assertEquals(referenceId, foundTransaction.get().getReference());
    }
    
    @Test
    void shouldFindTransactionByReferenceIgnoreCase() {
        // given
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        String referenceId = "Test-Reference-123";
        
        Transaction transaction = new Transaction(
            fromAccountId, toAccountId, new BigDecimal("100.0000"), 
            TransactionType.TRANSFER, Currency.EUR, referenceId
        );
        
        transactionRepository.save(transaction);
        entityManager.flush();
        entityManager.clear();

        // when - search with lowercase
        Optional<Transaction> foundTransaction1 = transactionRepository.findByReferenceIgnoreCase("test-reference-123");
        // when - search with uppercase
        Optional<Transaction> foundTransaction2 = transactionRepository.findByReferenceIgnoreCase("TEST-REFERENCE-123");

        // then
        assertTrue(foundTransaction1.isPresent(), "Should find transaction with lowercase reference");
        assertTrue(foundTransaction2.isPresent(), "Should find transaction with uppercase reference");
        assertEquals(referenceId, foundTransaction1.get().getReference());
        assertEquals(referenceId, foundTransaction2.get().getReference());
    }
    
    @Test
    void shouldEnforceReferenceUniqueness() {
        // given
        UUID fromAccountId1 = UUID.randomUUID();
        UUID toAccountId1 = UUID.randomUUID();
        UUID fromAccountId2 = UUID.randomUUID();
        UUID toAccountId2 = UUID.randomUUID();
        String sameReferenceId = "DUPLICATE-REF-" + UUID.randomUUID().toString();
        
        Transaction transaction1 = new Transaction(
            fromAccountId1, toAccountId1, new BigDecimal("100.0000"), 
            TransactionType.TRANSFER, Currency.EUR, sameReferenceId
        );
        
        Transaction transaction2 = new Transaction(
            fromAccountId2, toAccountId2, new BigDecimal("200.0000"), 
            TransactionType.TRANSFER, Currency.EUR, sameReferenceId
        );
        
        // Save first transaction
        transactionRepository.save(transaction1);
        entityManager.flush();
        
        // Try to save second transaction with same reference
        transactionRepository.save(transaction2);
        
        // Should throw exception on flush due to unique constraint
        Exception ex = assertThrows(Exception.class, () -> entityManager.flush());
        assertTrue(
            ex instanceof DataIntegrityViolationException ||
            ex instanceof org.hibernate.exception.ConstraintViolationException,
            "Expected DataIntegrityViolationException or ConstraintViolationException, but got: " + ex
        );
    }
} 