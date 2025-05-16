package com.cubeia.wallet.controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

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

/**
 * Tests for exception handling in the wallet API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ExceptionHandlingTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    
    /**
     * Helper method to set account balance by creating system credit ledger entries.
     */
    private void setAccountBalance(Account account, BigDecimal balance) {
        // Create a system account as the source of the credit
        Account systemAccount = accountRepository.save(new Account(Currency.EUR, AccountType.SystemAccount.getInstance()));
        
        // Create a transaction to link the ledger entries
        Transaction transaction = new Transaction(
            systemAccount.getId(),
            account.getId(),
            balance,
            TransactionType.TRANSFER,
            account.getCurrency()
        );
        
        transaction = transactionRepository.save(transaction);
        
        // Create debit from system account
        LedgerEntry debitEntry = LedgerEntry.builder()
            .accountId(systemAccount.getId())
            .transactionId(transaction.getId())
            .entryType(EntryType.DEBIT)
            .amount(balance)
            .description("System credit for testing")
            .currency(account.getCurrency())
            .build();
        
        // Create credit to target account
        LedgerEntry creditEntry = LedgerEntry.builder()
            .accountId(account.getId())
            .transactionId(transaction.getId())
            .entryType(EntryType.CREDIT)
            .amount(balance)
            .description("System credit for testing")
            .currency(account.getCurrency())
            .build();
        
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);
    }

    /**
     * Helper method to get the base URL for the API.
     */
    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    public void getBalance_NonExistentAccount_Returns404() {
        // when - requesting balance of a non-existent account
        UUID nonExistentId = UUID.randomUUID();
        ResponseEntity<Object> response = restTemplate.getForEntity(
                getBaseUrl() + "/accounts/{id}/balance",
                Object.class,
                nonExistentId);

        // then - should get a 404 Not Found response
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        
        // and - error response should contain proper message
        Object body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof Map, "Response body should be a Map");
        Map<String, Object> responseBody = (Map<String, Object>) body;
        assertTrue(responseBody.get("message").toString().contains("Account not found"));
    }

    @Test
    public void transfer_InsufficientFunds_Returns400() {
        // given - create sender and receiver accounts
        Account sender = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        Account receiver = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        
        // set a low balance on sender
        setAccountBalance(sender, new BigDecimal("10.00"));
        
        // and - prepare a transfer request for more than the available balance
        TransferRequestDto transferRequest = new TransferRequestDto(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("100.00"),
                null,
                null);
        
        // when - attempting the transfer
        ResponseEntity<Object> response = restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Object.class);
        
        // then - should get a 400 Bad Request response
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        // and - error response should contain proper message
        Object body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof Map, "Response body should be a Map");
        Map<String, Object> responseBody = (Map<String, Object>) body;
        assertTrue(responseBody.get("message").toString().contains("Insufficient funds"));
    }

    @Test
    public void transfer_InvalidAmountZero_Returns400() {
        // given - create sender and receiver accounts
        Account sender = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        Account receiver = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        
        // and - prepare a transfer request with zero amount
        TransferRequestDto transferRequest = new TransferRequestDto(
                sender.getId(),
                receiver.getId(),
                BigDecimal.ZERO,
                null,
                null);
        
        // when - attempting the transfer
        ResponseEntity<Object> response = restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Object.class);
        
        // then - should get a 400 Bad Request response
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        // and - error response should contain a message about validation failure
        Object body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof Map, "Response body should be a Map");
        Map<String, Object> responseBody = (Map<String, Object>) body;
        String errorMessage = responseBody.get("message").toString().toLowerCase();
        assertTrue(errorMessage.contains("amount") || errorMessage.contains("greater than 0") || errorMessage.contains("positive"),
                "Error should mention amount or positive value requirement: " + errorMessage);
    }

    @Test
    public void transfer_CurrencyMismatch_Returns400() {
        // given - create sender and receiver accounts with different currencies
        Account sender = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        Account receiver = accountRepository.save(new Account(Currency.USD, AccountType.MainAccount.getInstance()));
        
        // set a balance on sender
        setAccountBalance(sender, new BigDecimal("100.00"));
        
        // and - prepare a transfer request
        TransferRequestDto transferRequest = new TransferRequestDto(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("50.00"),
                null,
                null);
        
        // when - attempting the transfer
        ResponseEntity<Object> response = restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Object.class);
        
        // then - should get a 400 Bad Request response
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        // and - error response should contain proper message about currency mismatch
        Object body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof Map, "Response body should be a Map");
        Map<String, Object> responseBody = (Map<String, Object>) body;
        assertTrue(responseBody.get("message").toString().contains("different currencies"));
    }

    @Test
    public void transfer_missingRequiredField_Returns400() {
        // given - a transfer request with null amount (missing required field)
        Map<String, Object> invalidRequest = new HashMap<>();
        invalidRequest.put("fromAccountId", UUID.randomUUID().toString());
        invalidRequest.put("toAccountId", UUID.randomUUID().toString());
        // amount is missing
        
        // when - attempting the transfer
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(invalidRequest);
        ResponseEntity<Object> response = restTemplate.exchange(
                getBaseUrl() + "/transfers",
                HttpMethod.POST,
                requestEntity,
                Object.class);
        
        // then - should get a 400 Bad Request response
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        // and - error response should mention the validation failure
        Object body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof Map, "Response body should be a Map");
        Map<String, Object> responseBody = (Map<String, Object>) body;
        String errorMessage = responseBody.get("message").toString().toLowerCase();
        assertTrue(errorMessage.contains("amount") || errorMessage.contains("missing") || errorMessage.contains("required"),
                "Error should mention amount or missing/required: " + errorMessage);
    }
} 