package com.cubeia.wallet.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.closeTo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cubeia.wallet.dto.AccountLedgerDTO;
import com.cubeia.wallet.dto.AccountStatementDTO;
import com.cubeia.wallet.dto.TransactionHistoryDTO;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.EntryType;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.service.ReportingService;

@WebMvcTest(ReportingController.class)
public class ReportingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportingService reportingService;

    private UUID accountId;
    private UUID transactionId;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
        now = LocalDateTime.now();
    }

    @Test
    void testGetTransactionHistory() throws Exception {
        // Arrange
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        
        TransactionHistoryDTO.LedgerEntryDTO entry1 = new TransactionHistoryDTO.LedgerEntryDTO(
                UUID.randomUUID(),
                fromAccountId,
                EntryType.DEBIT.name(),
                new BigDecimal("100.00"),
                now,
                "Test debit"
        );
        
        TransactionHistoryDTO.LedgerEntryDTO entry2 = new TransactionHistoryDTO.LedgerEntryDTO(
                UUID.randomUUID(),
                toAccountId,
                EntryType.CREDIT.name(),
                new BigDecimal("100.00"),
                now,
                "Test credit"
        );
        
        TransactionHistoryDTO history = new TransactionHistoryDTO(
                transactionId,
                fromAccountId,
                toAccountId,
                new BigDecimal("100.00"),
                Currency.EUR,
                TransactionType.TRANSFER,
                now,
                "TEST-REF-123",
                Arrays.asList(entry1, entry2)
        );
        
        when(reportingService.getTransactionHistory(transactionId)).thenReturn(history);

        // Act & Assert
        mockMvc.perform(get("/reports/transactions/{transactionId}", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.fromAccountId").value(fromAccountId.toString()))
                .andExpect(jsonPath("$.toAccountId").value(toAccountId.toString()))
                .andExpect(jsonPath("$.amount").value(is(100.0)))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.referenceId").value("TEST-REF-123"))
                .andExpect(jsonPath("$.ledgerEntries.length()").value(2))
                .andExpect(jsonPath("$.ledgerEntries[0].accountId").value(fromAccountId.toString()))
                .andExpect(jsonPath("$.ledgerEntries[0].entryType").value("DEBIT"))
                .andExpect(jsonPath("$.ledgerEntries[1].accountId").value(toAccountId.toString()))
                .andExpect(jsonPath("$.ledgerEntries[1].entryType").value("CREDIT"));
    }
    
    @Test
    void testGetAccountLedger() throws Exception {
        // Arrange
        AccountLedgerDTO.LedgerEntryWithBalanceDTO entry1 = new AccountLedgerDTO.LedgerEntryWithBalanceDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntryType.CREDIT,
                new BigDecimal("50.00"),
                now.minusDays(1),
                "Credit test",
                new BigDecimal("50.00")
        );
        
        AccountLedgerDTO.LedgerEntryWithBalanceDTO entry2 = new AccountLedgerDTO.LedgerEntryWithBalanceDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntryType.DEBIT,
                new BigDecimal("30.00"),
                now,
                "Debit test",
                new BigDecimal("20.00")
        );
        
        AccountLedgerDTO ledger = new AccountLedgerDTO(
                accountId,
                Currency.EUR,
                new BigDecimal("20.00"),
                Arrays.asList(entry2, entry1)
        );
        
        when(reportingService.getAccountLedger(eq(accountId), anyInt(), anyInt())).thenReturn(ledger);

        // Act & Assert
        mockMvc.perform(get("/reports/accounts/{accountId}/ledger", accountId)
                .param("pageSize", "10")
                .param("pageNumber", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.currentBalance").value(is(20.0)))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].entryType").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].amount").value(is(30.0)))
                .andExpect(jsonPath("$.entries[0].runningBalance").value(is(20.0)))
                .andExpect(jsonPath("$.entries[1].entryType").value("CREDIT"))
                .andExpect(jsonPath("$.entries[1].amount").value(is(50.0)))
                .andExpect(jsonPath("$.entries[1].runningBalance").value(is(50.0)));
    }
    
    @Test
    void testGetAccountStatement() throws Exception {
        // Arrange
        LocalDateTime startDate = now.minusDays(30);
        LocalDateTime endDate = now;
        
        AccountStatementDTO.TransactionSummaryDTO summary1 = new AccountStatementDTO.TransactionSummaryDTO(
                UUID.randomUUID(),
                now.minusDays(15),
                "Withdrawal",
                new BigDecimal("50.00"),
                false
        );
        
        AccountStatementDTO.TransactionSummaryDTO summary2 = new AccountStatementDTO.TransactionSummaryDTO(
                UUID.randomUUID(),
                now.minusDays(5),
                "Deposit",
                new BigDecimal("75.00"),
                true
        );
        
        AccountStatementDTO statement = new AccountStatementDTO(
                accountId,
                Currency.EUR,
                startDate,
                endDate,
                new BigDecimal("200.00"),
                new BigDecimal("225.00"),
                new BigDecimal("50.00"),
                new BigDecimal("75.00"),
                2,
                Arrays.asList(summary1, summary2)
        );
        
        when(reportingService.getAccountStatement(eq(accountId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(statement);

        // Format dates for URL
        String startDateStr = DateTimeFormatter.ISO_DATE_TIME.format(startDate);
        String endDateStr = DateTimeFormatter.ISO_DATE_TIME.format(endDate);

        // Act & Assert
        mockMvc.perform(get("/reports/accounts/{accountId}/statement", accountId)
                .param("startDate", startDateStr)
                .param("endDate", endDateStr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.openingBalance").value(is(200.0)))
                .andExpect(jsonPath("$.closingBalance").value(is(225.0)))
                .andExpect(jsonPath("$.totalDebits").value(is(50.0)))
                .andExpect(jsonPath("$.totalCredits").value(is(75.0)))
                .andExpect(jsonPath("$.totalTransactions").value(2))
                .andExpect(jsonPath("$.transactions.length()").value(2))
                .andExpect(jsonPath("$.transactions[0].description").value("Withdrawal"))
                .andExpect(jsonPath("$.transactions[0].amount").value(is(50.0)))
                .andExpect(jsonPath("$.transactions[0].isCredit").value(false))
                .andExpect(jsonPath("$.transactions[1].description").value("Deposit"))
                .andExpect(jsonPath("$.transactions[1].amount").value(is(75.0)))
                .andExpect(jsonPath("$.transactions[1].isCredit").value(true));
    }
} 