package com.cubeia.wallet.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import com.cubeia.wallet.dto.TransferRequestDto;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.TransactionRepository;
import com.cubeia.wallet.service.DoubleEntryService;

/**
 * Integration tests for the wallet controller.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class WalletControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private DoubleEntryService doubleEntryService;
    
    private UUID systemAccountId;

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    public void setup() {
        // Clear transactions but keep accounts
        transactionRepository.deleteAll();
        
        // Find the system account created by DataInitializer
        Account systemAccount = accountRepository.findAll().stream()
            .filter(account -> "SYSTEM".equals(account.getAccountType().name()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("System account not found - ensure DataInitializer has run"));
        
        systemAccountId = systemAccount.getId();
        
        // Add funds to the system account (or ensure it has funds)
        if (doubleEntryService.calculateBalance(systemAccountId).compareTo(BigDecimal.ZERO) <= 0) {
            doubleEntryService.createSystemCreditEntry(
                systemAccountId,
                new BigDecimal("1000000.00"),  // 1 million units
                "System account initial funding for tests"
            );
        }
    }

    @Test
    public void createAccount_ShouldReturnCreatedAccountWithZeroBalance() {
        // when
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String requestBody = "{\"currency\":\"EUR\",\"accountType\":\"MAIN\"}";
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        
        ResponseEntity<Account> response = restTemplate.postForEntity(
                getBaseUrl() + "/accounts",
                request,
                Account.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        Account account = response.getBody();
        assertNotNull(account, "Account should not be null");
        assertThat(account.getId()).isNotNull();
        BigDecimal balance = getAccountBalance(account.getId());
        assertThat(balance).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void getBalance_ShouldReturnCorrectBalance() {
        // given
        Account account = createAccountWithBalance(new BigDecimal("123.45"));

        // when
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/accounts/{id}/balance",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {},
                account.getId());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        Object balanceObj = responseBody.get("balance");
        assertNotNull(balanceObj, "Balance should not be null");
        assertThat(new BigDecimal(balanceObj.toString()))
            .isEqualByComparingTo(new BigDecimal("123.45"));
    }

    @Test
    public void getBalance_ShouldReturn404ForNonExistentAccount() {
        // when
        UUID nonExistentId = UUID.randomUUID();
        ResponseEntity<String> response = restTemplate.getForEntity(
                getBaseUrl() + "/accounts/{id}/balance",
                String.class,
                nonExistentId);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void transfer_ShouldSuccessfullyTransferFundsBetweenAccounts() {
        // given
        Account fromAccount = createAccountWithBalance(new BigDecimal("500.00"));
        Account toAccount = createAccountWithBalance(BigDecimal.ZERO);

        TransferRequestDto transferRequest = new TransferRequestDto(
            fromAccount.getId(),
            toAccount.getId(),
            new BigDecimal("100.00"),
            UUID.randomUUID().toString(),
            "Test transfer between accounts"
        );

        // when
        ResponseEntity<Void> response = restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Void.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify the balances have been updated
        BigDecimal fromBalance = getAccountBalance(fromAccount.getId());
        BigDecimal toBalance = getAccountBalance(toAccount.getId());

        assertThat(fromBalance).isEqualByComparingTo(new BigDecimal("400.00"));
        assertThat(toBalance).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    public void transfer_ShouldFailWithInsufficientFunds() {
        // given
        Account fromAccount = createAccountWithBalance(new BigDecimal("50.00"));
        Account toAccount = createAccountWithBalance(BigDecimal.ZERO);

        TransferRequestDto transferRequest = new TransferRequestDto(
            fromAccount.getId(),
            toAccount.getId(),
            new BigDecimal("100.00"),
            UUID.randomUUID().toString(),
            "Test transfer with insufficient funds"
        );

        // when
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/transfers",
                HttpMethod.POST,
                new HttpEntity<>(transferRequest),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        
        assertThat(responseBody.containsKey("message")).isTrue();
        Object message = responseBody.get("message");
        assertNotNull(message, "Message field should not be null");
        String errorMessage = message.toString().toLowerCase();
        assertThat(errorMessage).containsAnyOf("insufficient funds");

        // Verify the balances remain unchanged
        BigDecimal fromBalance = getAccountBalance(fromAccount.getId());
        BigDecimal toBalance = getAccountBalance(toAccount.getId());

        assertThat(fromBalance).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(toBalance).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void transfer_ShouldFailWithNonExistentAccount() {
        // given
        Account toAccount = createAccountWithBalance(BigDecimal.ZERO);

        TransferRequestDto transferRequest = new TransferRequestDto(
            UUID.randomUUID(),
            toAccount.getId(),
            new BigDecimal("100.00"),
            UUID.randomUUID().toString(),
            "Test transfer with non-existent account"
        );

        // when
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/transfers",
                HttpMethod.POST,
                new HttpEntity<>(transferRequest),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        
        assertThat(responseBody.containsKey("message")).isTrue();
        Object message = responseBody.get("message");
        assertNotNull(message, "Message field should not be null");
        String errorMessage = message.toString().toLowerCase();
        assertThat(errorMessage).containsAnyOf("not found");
    }

    @Test
    public void transfer_ShouldRejectInvalidAmount() {
        // given
        Account fromAccount = createAccountWithBalance(new BigDecimal("100.00"));
        Account toAccount = createAccountWithBalance(BigDecimal.ZERO);

        TransferRequestDto transferRequest = new TransferRequestDto(
            fromAccount.getId(),
            toAccount.getId(),
            new BigDecimal("-50.00"),
            UUID.randomUUID().toString(),
            "Test transfer with invalid amount"
        );

        // when
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/transfers",
                HttpMethod.POST,
                new HttpEntity<>(transferRequest),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        
        assertThat(responseBody.containsKey("message")).isTrue();
        Object message = responseBody.get("message");
        assertNotNull(message, "Message field should not be null");
        String errorMessage = message.toString().toLowerCase();
        assertThat(errorMessage).containsAnyOf("amount", "negative", "positive", "greater than 0");
    }

    @Test
    public void getTransactions_ShouldReturnAccountTransactions() {
        // given
        Account fromAccount = createAccountWithBalance(new BigDecimal("500.00"));
        Account toAccount = createAccountWithBalance(BigDecimal.ZERO);

        // Perform a transfer to create a transaction
        TransferRequestDto transferRequest = new TransferRequestDto(
            fromAccount.getId(),
            toAccount.getId(),
            new BigDecimal("100.00"),
            UUID.randomUUID().toString(),
            "Test transaction for getTransactions test"
        );

        ResponseEntity<Void> transferResponse = restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Void.class);
                
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // when - get sender's transactions
        ResponseEntity<List<Map<String, Object>>> fromResponse = restTemplate.exchange(
                getBaseUrl() + "/accounts/{id}/transactions",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {},
                fromAccount.getId());

        // when - get receiver's transactions
        ResponseEntity<List<Map<String, Object>>> toResponse = restTemplate.exchange(
                getBaseUrl() + "/accounts/{id}/transactions",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {},
                toAccount.getId());

        // then
        assertThat(fromResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> fromTransactions = fromResponse.getBody();
        assertNotNull(fromTransactions, "Transactions list should not be null");
        // Verify that transactions are present
        assertThat(fromTransactions.size()).isGreaterThan(0);

        assertThat(toResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> toTransactions = toResponse.getBody();
        assertNotNull(toTransactions, "Transactions list should not be null");
        assertThat(toTransactions.size()).isGreaterThan(0);
    }

    @Test
    public void getTransactions_ShouldReturn404ForNonExistentAccount() {
        // when
        UUID nonExistentId = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/accounts/{id}/transactions",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {},
                nonExistentId);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void concurrentTransfers_ShouldMaintainDataConsistency() throws InterruptedException {
        // given
        Account fromAccount = createAccountWithBalance(new BigDecimal("1000.00"));
        Account toAccount = createAccountWithBalance(BigDecimal.ZERO);

        final int numOfThreads = 10;
        final ExecutorService executorService = Executors.newFixedThreadPool(numOfThreads);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(numOfThreads);

        // when - execute transfers concurrently
        for (int i = 0; i < numOfThreads; i++) {
            final String referenceId = "CONCURRENT-" + UUID.randomUUID().toString();
            final TransferRequestDto transferRequest = new TransferRequestDto(
                fromAccount.getId(),
                toAccount.getId(),
                new BigDecimal("100.00"),
                referenceId,
                "Concurrent transfer test #" + i
            );
            
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for signal to start
                    ResponseEntity<Void> response = restTemplate.postForEntity(
                            getBaseUrl() + "/transfers",
                            transferRequest,
                            Void.class);
                    
                    // We only care that it completes, not about the specific response
                    // as some requests might fail due to concurrent updates
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    // Log the exception but continue
                    System.err.println("Error during concurrent transfer: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Signal all threads to start
        boolean completed = endLatch.await(30, TimeUnit.SECONDS); // Wait for all threads to finish
        executorService.shutdown();

        // then
        assertTrue(completed, "All transfers should complete within timeout");

        // Verify the final balances - may need to wait briefly for persistence
        Thread.sleep(500); // Small delay to ensure all DB operations are complete
        
        BigDecimal fromBalance = getAccountBalance(fromAccount.getId());
        BigDecimal toBalance = getAccountBalance(toAccount.getId());

        // The from account should have had 1000 and sent 100 x 10 = 1000
        assertThat(fromBalance).isEqualByComparingTo(BigDecimal.ZERO);
        // The to account should have received 100 x 10 = 1000 
        assertThat(toBalance).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    // Helper methods

    private Account createAccountWithBalance(BigDecimal initialBalance) {
        // Create an account
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String requestBody = "{\"currency\":\"EUR\",\"accountType\":\"MAIN\"}";
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        
        ResponseEntity<Account> createResponse = restTemplate.postForEntity(
                getBaseUrl() + "/accounts",
                request,
                Account.class);
        
        Account account = createResponse.getBody();
        assertNotNull(account, "Created account should not be null");
        
        // If initial balance is not zero, create a deposit transaction
        if (initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            // Use the transfer endpoint to fund the account from a system account
            TransferRequestDto transferRequest = new TransferRequestDto(
                systemAccountId,
                account.getId(),
                initialBalance,
                UUID.randomUUID().toString(), // Add unique reference ID for idempotency
                "Initial funding for test account"
            );
            
            ResponseEntity<Void> transferResponse = restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Void.class
            );
            
            assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            
            // Verify the account was properly funded
            BigDecimal balance = getAccountBalance(account.getId());
            assertThat(balance).isEqualByComparingTo(initialBalance);
        }
        
        return account;
    }
    
    /**
     * Creates a system account with a very large balance for use in tests.
     * This method is no longer needed since we're using the system account
     * created by DataInitializer, but kept for backward compatibility.
     * 
     * @return the ID of the system account
     */
    private UUID createSystemAccount() {
        // Simply return the known system account ID
        return systemAccountId;
    }
    
    private BigDecimal getAccountBalance(UUID accountId) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                getBaseUrl() + "/accounts/{id}/balance",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {},
                accountId);
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        Object balance = body.get("balance");
        assertNotNull(balance, "Balance field should not be null");
        return new BigDecimal(balance.toString());
    }
} 