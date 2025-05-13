package com.cubeia.wallet.controller;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.cubeia.wallet.repository.AccountRepository;

/**
 * Tests for exception handling in the wallet API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ExceptionHandlingTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    /**
     * Helper method to set account balance using reflection (for testing).
     */
    private void setAccountBalance(Account account, BigDecimal balance) {
        try {
            Field balanceField = Account.class.getDeclaredField("balance");
            balanceField.setAccessible(true);
            balanceField.set(account, balance);
            accountRepository.save(account);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set balance", e);
        }
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
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
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
                new BigDecimal("100.00"));
        
        // when - attempting the transfer
        ResponseEntity<Object> response = restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Object.class);
        
        // then - should get a 400 Bad Request response
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        // and - error response should contain proper message
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
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
                BigDecimal.ZERO);
        
        // when - attempting the transfer
        ResponseEntity<Object> response = restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Object.class);
        
        // then - should get a 400 Bad Request response
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        // and - error response should contain proper message about validation failure
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        Map<String, Object> fieldErrors = (Map<String, Object>) responseBody.get("fieldErrors");
        assertTrue(fieldErrors.containsKey("amount"));
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
                new BigDecimal("50.00"));
        
        // when - attempting the transfer
        ResponseEntity<Object> response = restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Object.class);
        
        // then - should get a 400 Bad Request response
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        // and - error response should contain proper message about currency mismatch
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertTrue(responseBody.get("message").toString().contains("Currency mismatch"));
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
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        Map<String, Object> fieldErrors = (Map<String, Object>) responseBody.get("fieldErrors");
        assertTrue(fieldErrors.containsKey("amount"));
    }
} 