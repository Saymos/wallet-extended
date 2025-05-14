package com.cubeia.wallet;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import com.cubeia.wallet.exception.InvalidTransactionException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.TransactionRepository;
import com.cubeia.wallet.service.TransactionService;

/**
 * Tests for verifying idempotent behavior of transactions with reference IDs.
 * These tests ensure that transactions with the same reference ID are only executed once,
 * which is critical for financial systems to prevent duplicate transactions.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class TransactionIdempotencyTest {

    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private TransactionService transactionService;
    
    private Account sourceAccount;
    private Account destinationAccount;
    private final BigDecimal initialBalance = new BigDecimal("1000.00");
    private final BigDecimal transferAmount = new BigDecimal("100.00");
    
    @BeforeEach
    @Transactional
    public void setup() {
        // Create accounts with initial balances
        sourceAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        sourceAccount = accountRepository.save(sourceAccount);
        
        destinationAccount = new Account(Currency.EUR, AccountType.MainAccount.getInstance());
        destinationAccount = accountRepository.save(destinationAccount);
        
        // Set the source account balance directly (as we're in a test environment)
        try {
            Field balanceField = Account.class.getDeclaredField("balance");
            balanceField.setAccessible(true);
            balanceField.set(sourceAccount, initialBalance);
            accountRepository.save(sourceAccount);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set balance using reflection", e);
        }
        
        // Reload accounts from database to ensure they have proper state
        sourceAccount = accountRepository.findById(sourceAccount.getId()).orElseThrow();
        destinationAccount = accountRepository.findById(destinationAccount.getId()).orElseThrow();
    }
    
    /**
     * Test that a transaction with a reference ID is successfully processed the first time.
     */
    @Test
    @Transactional
    public void testFirstTransactionWithReferenceId() {
        // First transaction with a reference ID
        String referenceId = "TEST-REF-001";
        
        Transaction transaction = transactionService.transfer(
                sourceAccount.getId(),
                destinationAccount.getId(),
                transferAmount,
                referenceId
        );
        
        assertNotNull(transaction, "Transaction should be created");
        assertEquals(referenceId, transaction.getReference(), "Transaction should have the correct reference ID");
        
        // Verify account balances
        Account updatedSource = accountRepository.findById(sourceAccount.getId()).orElseThrow();
        Account updatedDestination = accountRepository.findById(destinationAccount.getId()).orElseThrow();
        
        assertEquals(0, initialBalance.subtract(transferAmount).compareTo(updatedSource.getBalance()),
                "Source account should be debited");
        assertEquals(0, transferAmount.compareTo(updatedDestination.getBalance()),
                "Destination account should be credited");
    }
    
    /**
     * Test that attempting to process a transaction with the same reference ID twice
     * returns the original transaction and doesn't modify account balances again.
     */
    @Test
    @Transactional
    public void testDuplicateTransactionWithSameReferenceId() {
        // First transaction with a reference ID
        String referenceId = "TEST-REF-002";
        
        Transaction firstTransaction = transactionService.transfer(
                sourceAccount.getId(),
                destinationAccount.getId(),
                transferAmount,
                referenceId
        );
        
        // Get updated account balances after first transaction
        Account sourceAfterFirst = accountRepository.findById(sourceAccount.getId()).orElseThrow();
        Account destinationAfterFirst = accountRepository.findById(destinationAccount.getId()).orElseThrow();
        BigDecimal sourceBalanceAfterFirst = sourceAfterFirst.getBalance();
        BigDecimal destBalanceAfterFirst = destinationAfterFirst.getBalance();
        
        // Attempt second transaction with the same reference ID
        Transaction secondTransaction = transactionService.transfer(
                sourceAccount.getId(),
                destinationAccount.getId(),
                transferAmount,
                referenceId
        );
        
        // The second call should return the existing transaction without creating a new one
        assertNotNull(secondTransaction, "Transaction should be returned");
        assertEquals(firstTransaction.getId(), secondTransaction.getId(), 
                "The same transaction should be returned for duplicate reference IDs");
        
        // Get account balances after the second attempt
        Account sourceAfterSecond = accountRepository.findById(sourceAccount.getId()).orElseThrow();
        Account destinationAfterSecond = accountRepository.findById(destinationAccount.getId()).orElseThrow();
        
        // Verify that balances haven't changed after the second attempt
        assertEquals(0, sourceBalanceAfterFirst.compareTo(sourceAfterSecond.getBalance()),
                "Source account balance should not change after duplicate transaction attempt");
        assertEquals(0, destBalanceAfterFirst.compareTo(destinationAfterSecond.getBalance()),
                "Destination account balance should not change after duplicate transaction attempt");
        
        // Verify only one transaction with this reference ID exists in the database
        List<Transaction> transactionsWithReferenceId = transactionRepository.findAllByReference(referenceId);
        assertEquals(1, transactionsWithReferenceId.size(), 
                "Only one transaction with this reference ID should exist");
    }
    
    /**
     * Test behavior when attempting to process a transaction with the same reference ID
     * but different parameters (different amount or different accounts).
     */
    @Test
    @Transactional
    public void testDuplicateReferenceIdWithDifferentParameters() {
        // First transaction with a reference ID
        String referenceId = "TEST-REF-003";
        BigDecimal differentAmount = new BigDecimal("50.00");
        
        Transaction firstTransaction = transactionService.transfer(
                sourceAccount.getId(),
                destinationAccount.getId(),
                transferAmount,
                referenceId
        );
        
        // Attempt transaction with same reference ID but different amount
        InvalidTransactionException exception = assertThrows(InvalidTransactionException.class, () -> {
            transactionService.transfer(
                    sourceAccount.getId(),
                    destinationAccount.getId(),
                    differentAmount,
                    referenceId
            );
        });
        
        assertTrue(exception.getMessage().contains("reference ID"),
                "Exception should mention reference ID conflict");
        
        // Verify only the original transaction exists with this reference ID
        assertTrue(transactionRepository.findByReference(referenceId).isPresent(),
                "The transaction with the reference ID should exist");
        assertEquals(firstTransaction.getId(), 
                transactionRepository.findByReference(referenceId).get().getId(),
                "The found transaction should match the original one");
    }
    
    /**
     * Test behavior with null reference IDs.
     */
    @Test
    @Transactional
    public void testTransactionsWithNullReferenceId() {
        // First transaction with null reference ID
        Transaction firstTransaction = transactionService.transfer(
                sourceAccount.getId(),
                destinationAccount.getId(),
                transferAmount,
                null
        );
        
        assertNull(firstTransaction.getReference(), "Transaction should have null reference ID");
        
        // Second transaction with null reference ID
        Transaction secondTransaction = transactionService.transfer(
                sourceAccount.getId(),
                destinationAccount.getId(),
                transferAmount,
                null
        );
        
        assertNull(secondTransaction.getReference(), "Second transaction should also have null reference ID");
        assertNotEquals(firstTransaction.getId(), secondTransaction.getId(),
                "Transactions with null reference IDs should be treated as separate transactions");
        
        // Verify account balances
        Account updatedSource = accountRepository.findById(sourceAccount.getId()).orElseThrow();
        Account updatedDestination = accountRepository.findById(destinationAccount.getId()).orElseThrow();
        
        assertEquals(0, initialBalance.subtract(transferAmount.multiply(new BigDecimal("2"))).compareTo(updatedSource.getBalance()),
                "Source account should be debited twice");
        assertEquals(0, transferAmount.multiply(new BigDecimal("2")).compareTo(updatedDestination.getBalance()),
                "Destination account should be credited twice");
    }
} 