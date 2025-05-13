package com.cubeia.wallet.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    public void setup() {
        // Clean up the repositories before each test
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
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
        assertThat(response.getBody().getBalance().compareTo(BigDecimal.ZERO)).isEqualTo(0);
    }

    @Test
    public void getBalance_ShouldReturnCorrectBalance() {
        // given
        Account account = createAccountWithBalance(new BigDecimal("100.00"));

        // when
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/accounts/{id}/balance",
                Map.class,
                account.getId());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(new BigDecimal(response.getBody().get("balance").toString()))
            .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    public void getBalance_ShouldReturn404ForNonExistentAccount() {
        // when
        ResponseEntity<String> response = restTemplate.getForEntity(
                getBaseUrl() + "/accounts/999/balance",
                String.class);

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
            new BigDecimal("100.00")
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
            new BigDecimal("100.00")
        );

        // when
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Map.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertTrue(response.getBody().get("message").toString().contains("insufficient funds"));

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
            999L,
            toAccount.getId(),
            new BigDecimal("100.00")
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
            new BigDecimal("-50.00")
        );

        // when
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/transfers",
                transferRequest,
                Map.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertTrue(response.getBody().get("message").toString().contains("positive"));
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
            new BigDecimal("100.00")
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
        ResponseEntity<String> response = restTemplate.getForEntity(
                getBaseUrl() + "/accounts/999/transactions",
                String.class);

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
            new BigDecimal("100.00")
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
        
        if (initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            // If we need a non-zero balance, we'll create a second account and transfer from it
            Account fundingAccount = createAccountWithZeroBalance();
            
            // Update the funding account balance directly in the database using reflection
            try {
                java.lang.reflect.Field balanceField = Account.class.getDeclaredField("balance");
                balanceField.setAccessible(true);
                balanceField.set(fundingAccount, initialBalance);
                accountRepository.save(fundingAccount);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set balance using reflection", e);
            }
            
            // Transfer from funding account to our target account
            TransferRequestDto transferRequest = new TransferRequestDto(
                fundingAccount.getId(),
                createResponse.getBody().getId(),
                initialBalance
            );
            
            restTemplate.postForEntity(
                    getBaseUrl() + "/transfers",
                    transferRequest,
                    Void.class);
        }
        
        return createResponse.getBody();
    }
    
    private Account createAccountWithZeroBalance() {
        ResponseEntity<Account> createResponse = restTemplate.postForEntity(
                getBaseUrl() + "/accounts",
                null,
                Account.class);
        return createResponse.getBody();
    }

    private BigDecimal getAccountBalance(Long accountId) {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/accounts/{id}/balance",
                Map.class,
                accountId);
        return new BigDecimal(response.getBody().get("balance").toString());
    }
} 