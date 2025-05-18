package com.cubeia.wallet.controller;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import com.cubeia.wallet.dto.TransferRequestDto;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.AccountType;
import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.TransactionRepository;
import com.cubeia.wallet.service.AccountService;
import com.cubeia.wallet.service.DoubleEntryService;

/**
 * Integration test for validations at the controller and service layer.
 * Tests that our validations continue to work correctly after refactoring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ValidationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private AccountService accountService;
    
    @Autowired
    private DoubleEntryService doubleEntryService;

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    public void setup() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    public void transfer_InsufficientFunds_ShouldReturnError() {
        // given - create accounts with zero balance
        Account fromAccount = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        Account toAccount = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        
        TransferRequestDto transferRequest = new TransferRequestDto(
                fromAccount.getId(),
                toAccount.getId(),
                new BigDecimal("100.00"),
                null,
                null);

        // when
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/transfers",
                HttpMethod.POST,
                new HttpEntity<>(transferRequest),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        // Check for null response body
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        
        // Now safely access the message field
        Object message = responseBody.get("message");
        assertNotNull(message, "Message should not be null");
        assertTrue(message.toString().contains("Insufficient funds"));
    }

    @Test
    public void transfer_CurrencyMismatch_ShouldReturnError() {
        // given - create accounts with different currencies
        Account fromAccount = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        Account toAccount = accountRepository.save(new Account(Currency.USD, AccountType.MainAccount.getInstance()));
        
        // Fund the account so insufficient funds isn't the issue
        doubleEntryService.createSystemCreditEntry(
            fromAccount.getId(), 
            new BigDecimal("500.00"), 
            "Test initial balance"
        );
        
        TransferRequestDto transferRequest = new TransferRequestDto(
                fromAccount.getId(),
                toAccount.getId(),
                new BigDecimal("100.00"),
                null,
                null);

        // when
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/transfers",
                HttpMethod.POST,
                new HttpEntity<>(transferRequest),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        // Check for null response body
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        
        // Now safely access the message field
        Object message = responseBody.get("message");
        assertNotNull(message, "Message should not be null");
        String errorMessage = message.toString();
        assertTrue(errorMessage.toLowerCase().contains("currency mismatch") 
                || errorMessage.toLowerCase().contains("different currencies"),
                "Error message should include currency mismatch: " + errorMessage);
    }
    
    @Test
    public void transfer_DuplicateReferenceWithDifferentParameters_ShouldReturnError() {
        // given
        Account account1 = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        Account account2 = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        Account account3 = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        
        // Fund account1
        doubleEntryService.createSystemCreditEntry(
            account1.getId(), 
            new BigDecimal("1000.00"), 
            "Test initial balance"
        );
        
        String referenceId = "test-reference-id";
        
        // First transfer succeeds
        TransferRequestDto transferRequest1 = new TransferRequestDto(
                account1.getId(),
                account2.getId(),
                new BigDecimal("100.00"),
                referenceId,
                null);
        
        restTemplate.exchange(
                getBaseUrl() + "/transfers", 
                HttpMethod.POST,
                new HttpEntity<>(transferRequest1),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        
        // Second transfer with same reference but different parameters
        TransferRequestDto transferRequest2 = new TransferRequestDto(
                account1.getId(),
                account3.getId(),  // Different destination
                new BigDecimal("100.00"),
                referenceId,
                null);

        // when
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/transfers",
                HttpMethod.POST,
                new HttpEntity<>(transferRequest2),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        // Check for null response body
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        
        // Now safely access the message field
        Object message = responseBody.get("message");
        assertNotNull(message, "Message should not be null");
        assertTrue(message.toString().contains("already exists with different parameters"));
    }
    
    @Test
    public void transfer_AccountNotFound_ShouldReturnError() {
        // given
        Account toAccount = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        UUID nonExistentAccountId = UUID.randomUUID();
        
        TransferRequestDto transferRequest = new TransferRequestDto(
                nonExistentAccountId,
                toAccount.getId(),
                new BigDecimal("100.00"),
                null,
                null);

        // when
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/transfers",
                HttpMethod.POST,
                new HttpEntity<>(transferRequest),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        // then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        
        // Check for null response body
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        
        // Now safely access the message field
        Object message = responseBody.get("message");
        assertNotNull(message, "Message should not be null");
        assertTrue(message.toString().contains("Account not found"));
    }
    
    @Test
    public void transfer_ValidParameters_ShouldSucceed() {
        // given
        Account fromAccount = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        Account toAccount = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        
        // Fund account
        doubleEntryService.createSystemCreditEntry(
            fromAccount.getId(), 
            new BigDecimal("500.00"), 
            "Test initial balance"
        );
        
        TransferRequestDto transferRequest = new TransferRequestDto(
                fromAccount.getId(),
                toAccount.getId(),
                new BigDecimal("100.00"),
                null,
                null);

        // when
        ResponseEntity<Void> response = restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Void.class);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        // Verify the balances were updated correctly
        BigDecimal fromBalance = accountService.getBalance(fromAccount.getId());
        BigDecimal toBalance = accountService.getBalance(toAccount.getId());
        
        // Use compareTo for BigDecimal comparison
        assertEquals(0, new BigDecimal("400.00").compareTo(fromBalance), 
                "Expected 400.00, but was " + fromBalance);
        assertEquals(0, new BigDecimal("100.00").compareTo(toBalance),
                "Expected 100.00, but was " + toBalance);
    }
} 