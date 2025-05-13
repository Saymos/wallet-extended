package com.cubeia.wallet.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

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
        assertThat(transaction).isNotNull();
        assertThat(transaction.getId()).isNotNull();
        assertThat(transaction.getFromAccountId()).isEqualTo(fromAccountId);
        assertThat(transaction.getToAccountId()).isEqualTo(toAccountId);
        assertThat(transaction.getAmount()).isEqualByComparingTo(new BigDecimal("100.0000"));
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(transaction.getCurrency()).isEqualTo(Currency.EUR);
        assertThat(transaction.getTimestamp()).isNotNull();
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
        assertThat(foundTransaction).isPresent();
        assertThat(foundTransaction.get().getId()).isEqualTo(savedTransaction.getId());
        assertThat(foundTransaction.get().getFromAccountId()).isEqualTo(fromAccountId);
        assertThat(foundTransaction.get().getToAccountId()).isEqualTo(toAccountId);
        assertThat(foundTransaction.get().getAmount()).isEqualByComparingTo(new BigDecimal("200.0000"));
        assertThat(foundTransaction.get().getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(foundTransaction.get().getCurrency()).isEqualTo(Currency.USD);
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
        assertThat(accountTransactions).hasSize(2);
        
        // Verify first transaction is in the result (account1 sending to account2)
        assertThat(accountTransactions).anyMatch(t -> 
            t.getFromAccountId().equals(account1Id) && t.getToAccountId().equals(account2Id));
        
        // Verify second transaction is in the result (account3 sending to account1)
        assertThat(accountTransactions).anyMatch(t -> 
            t.getFromAccountId().equals(account3Id) && t.getToAccountId().equals(account1Id));
    }

    @Test
    void shouldReturnEmptyListForAccountWithNoTransactions() {
        // when
        List<Transaction> accountTransactions = transactionRepository.findByAccountId(UUID.randomUUID());

        // then
        assertThat(accountTransactions).isEmpty();
    }
} 