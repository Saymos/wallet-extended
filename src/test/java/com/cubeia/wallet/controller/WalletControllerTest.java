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
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
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
        // Clear database for clean test
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        
        // Create a system account with a large balance for testing
        systemAccountId = createSystemAccount();
    }

    @Test
    public void createAccount_ShouldReturnCreatedAccountWithZeroBalance() {
        // when
        ResponseEntity<Account> response = restTemplate.postForEntity(
                getBaseUrl() + "/accounts",
                null,
                Account.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
        BigDecimal balance = getAccountBalance(response.getBody().getId());
        assertThat(balance).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void getBalance_ShouldReturnCorrectBalance() {
        // given
        Account account = createAccountWithBalance(new BigDecimal("123.45"));

        // when
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/accounts/{id}/balance",
                Map.class,
                account.getId());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(new BigDecimal(response.getBody().get("balance").toString()))
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
            null,
            null
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

        assertThat(fromBalance.compareTo(new BigDecimal("400.00"))).isEqualTo(0);
        assertThat(toBalance.compareTo(new BigDecimal("100.00"))).isEqualTo(0);
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
            null,
            null
        );

        // when
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Map.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertTrue(response.getBody().get("message").toString().contains("Insufficient funds"));

        // Verify the balances remain unchanged
        BigDecimal fromBalance = getAccountBalance(fromAccount.getId());
        BigDecimal toBalance = getAccountBalance(toAccount.getId());

        assertThat(fromBalance.compareTo(new BigDecimal("50.00"))).isEqualTo(0);
        assertThat(toBalance.compareTo(BigDecimal.ZERO)).isEqualTo(0);
    }

    @Test
    public void transfer_ShouldFailWithNonExistentAccount() {
        // given
        Account toAccount = createAccountWithBalance(BigDecimal.ZERO);

        TransferRequestDto transferRequest = new TransferRequestDto(
            UUID.randomUUID(),
            toAccount.getId(),
            new BigDecimal("100.00"),
            null,
            null
        );

        // when
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Map.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertTrue(response.getBody().get("message").toString().contains("not found"));
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
            null,
            null
        );

        // when
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Map.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().containsKey("message")).isTrue();
        String errorMessage = response.getBody().get("message").toString().toLowerCase();
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
            null,
            null
        );

        restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Void.class);

        // when - get sender's transactions
        ResponseEntity<List> fromResponse = restTemplate.getForEntity(
                getBaseUrl() + "/accounts/{id}/transactions",
                List.class,
                fromAccount.getId());

        // when - get receiver's transactions
        ResponseEntity<List> toResponse = restTemplate.getForEntity(
                getBaseUrl() + "/accounts/{id}/transactions",
                List.class,
                toAccount.getId());

        // then
        assertThat(fromResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fromResponse.getBody()).isNotNull();
        // Only verify that transactions are present, not the exact count
        assertThat(fromResponse.getBody().size()).isGreaterThan(0);

        assertThat(toResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(toResponse.getBody()).isNotNull();
        assertThat(toResponse.getBody().size()).isGreaterThan(0);
    }

    @Test
    public void getTransactions_ShouldReturn404ForNonExistentAccount() {
        // when
        UUID nonExistentId = UUID.randomUUID();
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/accounts/{id}/transactions",
                Map.class,
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

        TransferRequestDto transferRequest = new TransferRequestDto(
            fromAccount.getId(),
            toAccount.getId(),
            new BigDecimal("100.00"),
            null,
            null
        );

        // when - execute transfers concurrently
        for (int i = 0; i < numOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for signal to start
                    restTemplate.postForEntity(
                            getBaseUrl() + "/transfers",
                            transferRequest,
                            Void.class);
                } catch (Exception e) {
                    e.printStackTrace();
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

        // Verify the final balances
        BigDecimal fromBalance = getAccountBalance(fromAccount.getId());
        BigDecimal toBalance = getAccountBalance(toAccount.getId());

        assertThat(fromBalance.compareTo(new BigDecimal("0.00"))).isEqualTo(0);
        assertThat(toBalance.compareTo(new BigDecimal("1000.00"))).isEqualTo(0);
    }

    // Helper methods

    private Account createAccountWithBalance(BigDecimal initialBalance) {
        // Create an account
        ResponseEntity<Account> createResponse = restTemplate.postForEntity(
                getBaseUrl() + "/accounts",
                null,
                Account.class);
        
        Account account = createResponse.getBody();
        
        // If initial balance is not zero, create a deposit transaction
        if (initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            // Use the transfer endpoint to fund the account from a system account
            TransferRequestDto transferRequest = new TransferRequestDto(
                systemAccountId,
                account.getId(),
                initialBalance,
                null,
                null
            );
            
            restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Void.class
            );
        }
        
        return account;
    }
    
    /**
     * Creates a system account with a very large balance for use in tests.
     * 
     * @return the ID of the created system account
     */
    private UUID createSystemAccount() {
        // Create a new system account
        ResponseEntity<Account> createResponse = restTemplate.postForEntity(
                getBaseUrl() + "/accounts",
                null,
                Account.class);
        
        Account systemAccount = createResponse.getBody();
        
        // Add a very large credit entry to the system account
        doubleEntryService.createSystemCreditEntry(
            systemAccount.getId(),
            new BigDecimal("1000000.00"),  // 1 million units
            "System account initial funding"
        );
        
        return systemAccount.getId();
    }
    
    private Account createAccountWithZeroBalance() {
        return createAccountWithBalance(BigDecimal.ZERO);
    }
    
    private BigDecimal getAccountBalance(UUID accountId) {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/accounts/{id}/balance",
                Map.class,
                accountId);
        return new BigDecimal(response.getBody().get("balance").toString());
    }
} 