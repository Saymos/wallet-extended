package com.cubeia.wallet.controller;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;

import com.cubeia.wallet.dto.TransferRequestDto;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.EntryType;
import com.cubeia.wallet.model.LedgerEntry;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.LedgerEntryRepository;
import com.cubeia.wallet.repository.TransactionRepository;
import com.cubeia.wallet.service.DoubleEntryService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for the wallet controller's idempotency features.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class WalletControllerIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    
    @Autowired
    private DoubleEntryService doubleEntryService;
    
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
        
        // Set the source account balance by creating ledger entries
        Account systemAccount = accountRepository.save(new Account(Currency.EUR, AccountType.SystemAccount.getInstance()));
        
        // Create a transaction to link the ledger entries
        Transaction transaction = new Transaction(
            systemAccount.getId(),
            sourceAccount.getId(),
            initialBalance,
            TransactionType.TRANSFER,
            sourceAccount.getCurrency()
        );
        
        transaction = transactionRepository.save(transaction);
        
        // Create debit from system account
        LedgerEntry debitEntry = LedgerEntry.builder()
            .accountId(systemAccount.getId())
            .transactionId(transaction.getId())
            .entryType(EntryType.DEBIT)
            .amount(initialBalance)
            .description("System credit for testing")
            .currency(sourceAccount.getCurrency())
            .build();
        
        // Create credit to source account
        LedgerEntry creditEntry = LedgerEntry.builder()
            .accountId(sourceAccount.getId())
            .transactionId(transaction.getId())
            .entryType(EntryType.CREDIT)
            .amount(initialBalance)
            .description("System credit for testing")
            .currency(sourceAccount.getCurrency())
            .build();
        
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);
        
        // Refresh accounts
        sourceAccount = accountRepository.findById(sourceAccount.getId()).orElseThrow();
        destinationAccount = accountRepository.findById(destinationAccount.getId()).orElseThrow();
    }
    
    @Test
    @Transactional
    public void testIdempotentTransfer() throws Exception {
        // Create transfer request with reference ID
        String referenceId = "TEST-API-REF-001";
        TransferRequestDto request = new TransferRequestDto(
            sourceAccount.getId(),
            destinationAccount.getId(),
            transferAmount,
            referenceId
        );
        
        // First request - should go through
        mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
            
        // Check balances after first request
        BigDecimal sourceBalanceAfterFirst = doubleEntryService.calculateBalance(sourceAccount.getId());
        BigDecimal destinationBalanceAfterFirst = doubleEntryService.calculateBalance(destinationAccount.getId());
        assertEquals(0, initialBalance.subtract(transferAmount).compareTo(sourceBalanceAfterFirst),
                "Source account should be debited");
        assertEquals(0, transferAmount.compareTo(destinationBalanceAfterFirst),
                "Destination account should be credited");
                
        // Second request with same reference ID - should be idempotent (no change)
        mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
            
        // Check that balances remain the same after second request
        BigDecimal sourceBalanceAfterSecond = doubleEntryService.calculateBalance(sourceAccount.getId());
        BigDecimal destinationBalanceAfterSecond = doubleEntryService.calculateBalance(destinationAccount.getId());
        
        assertEquals(sourceBalanceAfterFirst, sourceBalanceAfterSecond,
                "Source balance should not change after duplicate request");
        assertEquals(destinationBalanceAfterFirst, destinationBalanceAfterSecond,
                "Destination balance should not change after duplicate request");
                
        // Verify only one transaction was created
        assertEquals(1, transactionRepository.findAllByReference(referenceId).size(),
                "Only one transaction should exist with this reference ID");
    }
    
    @Test
    @Transactional
    public void testGetTransactionByReferenceId() throws Exception {
        // Create a transaction with a reference ID
        String referenceId = "TEST-API-REF-002";
        TransferRequestDto request = new TransferRequestDto(
            sourceAccount.getId(),
            destinationAccount.getId(),
            transferAmount,
            referenceId
        );
        
        // Execute the transfer
        mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
            
        // Get the transaction by reference ID
        mockMvc.perform(get("/transactions/reference/{referenceId}", referenceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount").value(transferAmount.doubleValue()))
            .andExpect(jsonPath("$.fromAccountId").value(sourceAccount.getId().toString()))
            .andExpect(jsonPath("$.toAccountId").value(destinationAccount.getId().toString()))
            .andExpect(jsonPath("$.reference").value(referenceId));
    }
    
    @Test
    @Transactional
    public void testGetTransactionByReferenceIdCaseInsensitive() throws Exception {
        // Create a transaction with a mixed-case reference ID
        String referenceId = "Test-CASE-Sensitivity-123";
        TransferRequestDto request = new TransferRequestDto(
            sourceAccount.getId(),
            destinationAccount.getId(),
            transferAmount,
            referenceId
        );
        
        // Execute the transfer
        mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
            
        // Get the transaction by reference ID with different case
        mockMvc.perform(get("/transactions/reference/{referenceId}", "test-case-sensitivity-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount").value(transferAmount.doubleValue()))
            .andExpect(jsonPath("$.fromAccountId").value(sourceAccount.getId().toString()))
            .andExpect(jsonPath("$.toAccountId").value(destinationAccount.getId().toString()))
            .andExpect(jsonPath("$.reference").value(referenceId)); // Should return original casing
            
        // Try with uppercase
        mockMvc.perform(get("/transactions/reference/{referenceId}", "TEST-CASE-SENSITIVITY-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reference").value(referenceId)); // Should return original casing
    }
} 