package com.cubeia.wallet.controller;

import java.math.BigDecimal;
import java.util.HashMap;
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

/**
 * Tests focused specifically on API validation at the controller level.
 * These tests ensure proper validation of all input parameters and
 * verify that appropriate errors are returned for invalid inputs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ValidationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    public void setup() {
        // Clear database for clean test
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    public void transfer_NullFromAccount_ShouldReturnValidationError() {
        // given - create a request with null fromAccountId
        Map<String, Object> request = new HashMap<>();
        request.put("toAccountId", UUID.randomUUID().toString());
        request.put("amount", "100.00");

        // when - making the request
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(request);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/transfers",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        // then - should get a validation error
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        
        Object message = responseBody.get("message");
        assertNotNull(message, "Message field should not be null");
        String errorMessage = message.toString();
        assertTrue(errorMessage.contains("Source account ID is required"));
    }

    @Test
    public void transfer_NullToAccount_ShouldReturnValidationError() {
        // given - create a request with null toAccountId
        Map<String, Object> request = new HashMap<>();
        request.put("fromAccountId", UUID.randomUUID().toString());
        request.put("amount", "100.00");

        // when - making the request
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(request);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/transfers",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        // then - should get a validation error
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        
        Object message = responseBody.get("message");
        assertNotNull(message, "Message field should not be null");
        String errorMessage = message.toString();
        assertTrue(errorMessage.contains("Destination account ID is required"));
    }

    @Test
    public void transfer_ZeroAmount_ShouldReturnValidationError() {
        // given
        Account fromAccount = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        Account toAccount = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        
        TransferRequestDto transferRequest = new TransferRequestDto(
                fromAccount.getId(),
                toAccount.getId(),
                BigDecimal.ZERO,
                null,
                null);

        // when
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/transfers",
                HttpMethod.POST,
                new HttpEntity<>(transferRequest),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        
        Object message = responseBody.get("message");
        assertNotNull(message, "Message field should not be null");
        String errorMessage = message.toString();
        assertTrue(errorMessage.contains("Amount must be positive"));
    }

    @Test
    public void transfer_NegativeAmount_ShouldReturnValidationError() {
        // given
        Account fromAccount = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        Account toAccount = accountRepository.save(new Account(Currency.EUR, AccountType.MainAccount.getInstance()));
        
        TransferRequestDto transferRequest = new TransferRequestDto(
                fromAccount.getId(),
                toAccount.getId(),
                new BigDecimal("-100.00"),
                null,
                null);

        // when
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/transfers",
                HttpMethod.POST,
                new HttpEntity<>(transferRequest),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        
        Object message = responseBody.get("message");
        assertNotNull(message, "Message field should not be null");
        String errorMessage = message.toString();
        assertTrue(errorMessage.contains("Amount must be positive"));
    }

    @Test
    public void transfer_NullAmount_ShouldReturnValidationError() {
        // given - create a request with null amount
        Map<String, Object> request = new HashMap<>();
        request.put("fromAccountId", UUID.randomUUID().toString());
        request.put("toAccountId", UUID.randomUUID().toString());
        // amount is missing

        // when - making the request
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(request);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/transfers",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        // then - should get a validation error
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        
        Object message = responseBody.get("message");
        assertNotNull(message, "Message field should not be null");
        String errorMessage = message.toString();
        assertTrue(errorMessage.contains("Amount is required"));
    }

    @Test
    public void transfer_InvalidAccountFormat_ShouldReturnError() {
        // given - create a request with invalid UUID format
        Map<String, Object> request = new HashMap<>();
        request.put("fromAccountId", "not-a-uuid");
        request.put("toAccountId", UUID.randomUUID().toString());
        request.put("amount", "100.00");

        // when - making the request
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(request);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/transfers",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        // then - should get a validation error
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        
        Object message = responseBody.get("message");
        assertNotNull(message, "Message field should not be null");
        String errorMessage = message.toString();
        assertTrue(errorMessage.contains("JSON"));
    }

    @Test
    public void transfer_InvalidAmountFormat_ShouldReturnError() {
        // given - create a request with invalid amount format
        Map<String, Object> request = new HashMap<>();
        request.put("fromAccountId", UUID.randomUUID().toString());
        request.put("toAccountId", UUID.randomUUID().toString());
        request.put("amount", "not-a-number");

        // when - making the request
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(request);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/transfers",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        // then - should get a validation error
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        
        Object message = responseBody.get("message");
        assertNotNull(message, "Message field should not be null");
        String errorMessage = message.toString();
        assertTrue(errorMessage.contains("JSON"));
    }
} 