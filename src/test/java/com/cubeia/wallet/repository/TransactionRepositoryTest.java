package com.cubeia.wallet.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.cubeia.wallet.model.Transaction;

@DataJpaTest
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void shouldSaveTransaction() {
        // given
        Transaction transaction = new Transaction();
        transaction.setFromAccountId(1L);
        transaction.setToAccountId(2L);
        transaction.setAmount(new BigDecimal("100.0000"));

        // when
        Transaction savedTransaction = transactionRepository.save(transaction);

        // then
        assertThat(savedTransaction.getId()).isNotNull();
        assertThat(savedTransaction.getFromAccountId()).isEqualTo(1L);
        assertThat(savedTransaction.getToAccountId()).isEqualTo(2L);
        assertThat(savedTransaction.getAmount()).isEqualByComparingTo(new BigDecimal("100.0000"));
        assertThat(savedTransaction.getTimestamp()).isNotNull();
    }

    @Test
    void shouldFindTransactionById() {
        // given
        Transaction transaction = new Transaction();
        transaction.setFromAccountId(3L);
        transaction.setToAccountId(4L);
        transaction.setAmount(new BigDecimal("200.0000"));
        
        Transaction savedTransaction = transactionRepository.save(transaction);

        // when
        Optional<Transaction> foundTransaction = transactionRepository.findById(savedTransaction.getId());

        // then
        assertThat(foundTransaction).isPresent();
        assertThat(foundTransaction.get().getId()).isEqualTo(savedTransaction.getId());
        assertThat(foundTransaction.get().getFromAccountId()).isEqualTo(3L);
        assertThat(foundTransaction.get().getToAccountId()).isEqualTo(4L);
        assertThat(foundTransaction.get().getAmount()).isEqualByComparingTo(new BigDecimal("200.0000"));
    }

    @Test
    void shouldFindTransactionsByAccountId() {
        // given
        Transaction transaction1 = new Transaction();
        transaction1.setFromAccountId(5L);
        transaction1.setToAccountId(6L);
        transaction1.setAmount(new BigDecimal("300.0000"));
        
        Transaction transaction2 = new Transaction();
        transaction2.setFromAccountId(7L);
        transaction2.setToAccountId(5L);
        transaction2.setAmount(new BigDecimal("400.0000"));
        
        Transaction transaction3 = new Transaction();
        transaction3.setFromAccountId(8L);
        transaction3.setToAccountId(9L);
        transaction3.setAmount(new BigDecimal("500.0000"));
        
        transactionRepository.saveAll(List.of(transaction1, transaction2, transaction3));

        // when
        List<Transaction> accountTransactions = transactionRepository.findByAccountId(5L);

        // then
        assertThat(accountTransactions).hasSize(2);
        
        // Verify first transaction is in the result (account 5 sending to account 6)
        assertThat(accountTransactions).anyMatch(t -> 
            t.getFromAccountId().equals(5L) && t.getToAccountId().equals(6L));
        
        // Verify second transaction is in the result (account 7 sending to account 5)
        assertThat(accountTransactions).anyMatch(t -> 
            t.getFromAccountId().equals(7L) && t.getToAccountId().equals(5L));
    }

    @Test
    void shouldReturnEmptyListForAccountWithNoTransactions() {
        // when
        List<Transaction> accountTransactions = transactionRepository.findByAccountId(999L);

        // then
        assertThat(accountTransactions).isEmpty();
    }
} 