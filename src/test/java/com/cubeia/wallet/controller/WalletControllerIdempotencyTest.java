package com.cubeia.wallet.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.cubeia.wallet.dto.TransferRequestDto;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.TransactionRepository;
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
        Account updatedSource = accountRepository.findById(sourceAccount.getId()).orElseThrow();
        Account updatedDestination = accountRepository.findById(destinationAccount.getId()).orElseThrow();
        
        assertEquals(0, initialBalance.subtract(transferAmount).compareTo(updatedSource.getBalance()),
                "Source account should be debited");
        assertEquals(0, transferAmount.compareTo(updatedDestination.getBalance()),
                "Destination account should be credited");
                
        // Second request with same reference ID - should be idempotent (no change)
        mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
            
        // Check that balances remain the same after second request
        Account sourceAfterSecond = accountRepository.findById(sourceAccount.getId()).orElseThrow();
        Account destinationAfterSecond = accountRepository.findById(destinationAccount.getId()).orElseThrow();
        
        assertEquals(updatedSource.getBalance(), sourceAfterSecond.getBalance(),
                "Source balance should not change after duplicate request");
        assertEquals(updatedDestination.getBalance(), destinationAfterSecond.getBalance(),
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
} 