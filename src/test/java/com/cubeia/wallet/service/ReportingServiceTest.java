package com.cubeia.wallet.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.cubeia.wallet.dto.AccountLedgerDTO;
import com.cubeia.wallet.dto.AccountStatementDTO;
import com.cubeia.wallet.dto.TransactionHistoryDTO;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.EntryType;
import com.cubeia.wallet.model.LedgerEntry;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.LedgerEntryRepository;
import com.cubeia.wallet.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
public class ReportingServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private ReportingService reportingService;

    private UUID accountId;
    private UUID transactionId;
    private Account account;
    private Transaction transaction;
    private LedgerEntry debitEntry;
    private LedgerEntry creditEntry;

    @BeforeEach
    void setUp() {
        reportingService = new ReportingService(accountRepository, transactionRepository, ledgerEntryRepository);

        // Setup test data
        accountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
        
        account = new Account(Currency.EUR, null);
        
        // Use reflection to set the ID field (which is normally handled by JPA)
        try {
            java.lang.reflect.Field idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(account, accountId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set account ID for test", e);
        }
        
        transaction = new Transaction(
                accountId,
                toAccountId,
                new BigDecimal("100.00"),
                TransactionType.TRANSFER,
                Currency.EUR
        );
        
        // Use reflection to set the ID field (which is normally handled by JPA)
        try {
            java.lang.reflect.Field idField = Transaction.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(transaction, transactionId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set transaction ID for test", e);
        }
        
        // Create ledger entries
        debitEntry = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("100.00"))
                .description("Test debit")
                .build();
        
        creditEntry = LedgerEntry.builder()
                .accountId(toAccountId)
                .transactionId(transactionId)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("100.00"))
                .description("Test credit")
                .build();
    }

    @Test
    void testGetTransactionHistory() {
        // Arrange
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(ledgerEntryRepository.findByTransactionId(transactionId)).thenReturn(Arrays.asList(debitEntry, creditEntry));

        // Act
        TransactionHistoryDTO history = reportingService.getTransactionHistory(transactionId);

        // Assert
        assertNotNull(history);
        assertEquals(transactionId, history.transactionId());
        assertEquals(accountId, history.fromAccountId());
        assertEquals(transaction.getToAccountId(), history.toAccountId());
        assertEquals(new BigDecimal("100.00"), history.amount());
        assertEquals(Currency.EUR, history.currency());
        assertEquals(TransactionType.TRANSFER, history.type());
        assertEquals(2, history.ledgerEntries().size());
    }

    @Test
    void testGetAccountLedger() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        
        LedgerEntry entry1 = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(UUID.randomUUID())
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("50.00"))
                .description("Credit test")
                .build();
        
        LedgerEntry entry2 = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(UUID.randomUUID())
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("30.00"))
                .description("Debit test")
                .build();
        
        List<LedgerEntry> entries = new ArrayList<>(Arrays.asList(entry2, entry1)); // Descending order by timestamp
        
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(ledgerEntryRepository.findByAccountIdOrderByTimestampDesc(eq(accountId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(entries));
        when(ledgerEntryRepository.findByAccountIdAndTimestampBeforeOrderByTimestampAsc(eq(accountId), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        // Act
        AccountLedgerDTO ledger = reportingService.getAccountLedger(accountId, 10, 0);

        // Assert
        assertNotNull(ledger);
        assertEquals(accountId, ledger.accountId());
        assertEquals(Currency.EUR, ledger.currency());
        assertEquals(2, ledger.entries().size());
        assertEquals(new BigDecimal("20.00"), ledger.currentBalance()); // 50 - 30 = 20
    }

    @Test
    void testGetAccountStatement() {
        // Arrange
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now();
        
        // Entry before the period
        LedgerEntry entry1 = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(UUID.randomUUID())
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("200.00"))
                .description("Opening balance")
                .build();
        
        // Entries in the period
        LedgerEntry entry2 = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(UUID.randomUUID())
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("50.00"))
                .description("Withdrawal")
                .build();
        
        LedgerEntry entry3 = LedgerEntry.builder()
                .accountId(accountId)
                .transactionId(UUID.randomUUID())
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("75.00"))
                .description("Deposit")
                .build();
        
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(ledgerEntryRepository.findByAccountIdAndTimestampBeforeOrderByTimestampAsc(accountId, startDate))
                .thenReturn(Arrays.asList(entry1));
        when(ledgerEntryRepository.findByAccountIdAndTimestampBetweenOrderByTimestampAsc(accountId, startDate, endDate))
                .thenReturn(Arrays.asList(entry2, entry3));

        // Act
        AccountStatementDTO statement = reportingService.getAccountStatement(accountId, startDate, endDate);

        // Assert
        assertNotNull(statement);
        assertEquals(accountId, statement.accountId());
        assertEquals(startDate, statement.startDate());
        assertEquals(endDate, statement.endDate());
        assertEquals(new BigDecimal("200.00"), statement.openingBalance());
        assertEquals(new BigDecimal("225.00"), statement.closingBalance()); // 200 - 50 + 75 = 225
        assertEquals(new BigDecimal("50.00"), statement.totalDebits());
        assertEquals(new BigDecimal("75.00"), statement.totalCredits());
        assertEquals(2, statement.totalTransactions());
        assertEquals(2, statement.transactions().size());
    }
} 