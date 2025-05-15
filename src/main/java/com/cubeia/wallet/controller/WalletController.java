package com.cubeia.wallet.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.cubeia.wallet.dto.BalanceResponseDto;
import com.cubeia.wallet.dto.TransferRequestDto;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.LedgerEntry;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.repository.TransactionRepository;
import com.cubeia.wallet.service.AccountService;
import com.cubeia.wallet.service.DoubleEntryService;
import com.cubeia.wallet.service.TransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST API controller for wallet operations.
 */
@RestController
@Tag(name = "Wallet API", description = "API endpoints for wallet operations")
public class WalletController {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final DoubleEntryService doubleEntryService;

    public WalletController(AccountService accountService, TransactionService transactionService, 
                            TransactionRepository transactionRepository, DoubleEntryService doubleEntryService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.doubleEntryService = doubleEntryService;
    }

    /**
     * Creates a new account with zero balance.
     *
     * @return the created account
     */
    @PostMapping("/accounts")
    @Operation(summary = "Create a new account", 
               description = "Creates a new account with zero balance")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Account created",
                     content = @Content(schema = @Schema(implementation = Account.class)))
    })
    public ResponseEntity<Account> createAccount() {
        Account account = accountService.createAccount();
        return new ResponseEntity<>(account, HttpStatus.CREATED);
    }

    /**
     * Gets the balance of an account.
     *
     * @param id the account ID
     * @return the account balance
     */
    @GetMapping("/accounts/{id}/balance")
    @Operation(summary = "Get account balance", 
               description = "Retrieves the current balance of an account calculated using double-entry bookkeeping")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Balance retrieved successfully",
                     content = @Content(schema = @Schema(implementation = BalanceResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<BalanceResponseDto> getBalance(
            @Parameter(description = "ID of the account") @PathVariable UUID id) {
        BigDecimal balance = accountService.getBalance(id);
        BalanceResponseDto response = new BalanceResponseDto(balance);
        return ResponseEntity.ok(response);
    }

    /**
     * Transfers funds between accounts.
     *
     * @param transferRequest the transfer request
     * @return empty response with HTTP status
     */
    @PostMapping("/transfers")
    @Operation(summary = "Transfer funds between accounts", 
               description = "Transfers funds from one account to another using double-entry bookkeeping. If a referenceId is provided, the operation is idempotent - multiple requests with the same referenceId will only execute the transfer once.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Transfer successful"),
        @ApiResponse(responseCode = "400", description = "Invalid request, insufficient funds, or referenceId conflict"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<Void> transfer(@RequestBody TransferRequestDto transferRequest) {
        transactionService.transfer(
                transferRequest.fromAccountId(),
                transferRequest.toAccountId(),
                transferRequest.amount(),
                transferRequest.referenceId()
        );
        return ResponseEntity.ok().build();
    }

    /**
     * Gets all transactions for an account.
     *
     * @param id the account ID
     * @return list of transactions involving the account
     */
    @GetMapping("/accounts/{id}/transactions")
    @Operation(summary = "Get account transactions", 
               description = "Retrieves all transactions involving an account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully",
                     content = @Content(schema = @Schema(implementation = Transaction.class))),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<List<Transaction>> getTransactions(
            @Parameter(description = "ID of the account") @PathVariable UUID id) {
        // First verify the account exists by getting the balance
        accountService.getBalance(id);
        
        // If we get here, the account exists, so fetch transactions
        List<Transaction> transactions = transactionService.getAccountTransactions(id);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Gets a transaction by its reference ID.
     *
     * @param referenceId the reference ID to look up
     * @return the transaction with the specified reference ID, or 404 if not found
     */
    @GetMapping("/transactions/reference/{referenceId}")
    @Operation(summary = "Get transaction by reference ID", 
               description = "Retrieves a transaction by its reference ID, useful for idempotency verification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Transaction found",
                     content = @Content(schema = @Schema(implementation = Transaction.class))),
        @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<Transaction> getTransactionByReference(
            @Parameter(description = "Reference ID of the transaction") @PathVariable String referenceId) {
        return transactionRepository.findByReferenceIgnoreCase(referenceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Gets all ledger entries for an account.
     *
     * @param id the account ID
     * @return list of ledger entries for the account
     */
    @GetMapping("/accounts/{id}/ledger-entries")
    @Operation(summary = "Get account ledger entries", 
               description = "Retrieves all ledger entries for an account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Ledger entries retrieved successfully",
                     content = @Content(schema = @Schema(implementation = LedgerEntry.class))),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<List<LedgerEntry>> getLedgerEntries(
            @Parameter(description = "ID of the account") @PathVariable UUID id) {
        List<LedgerEntry> entries = doubleEntryService.getAccountEntries(id);
        return ResponseEntity.ok(entries);
    }
    
    /**
     * Gets all ledger entries for a transaction.
     *
     * @param id the transaction ID
     * @return list of ledger entries for the transaction
     */
    @GetMapping("/transactions/{id}/ledger-entries")
    @Operation(summary = "Get transaction ledger entries", 
               description = "Retrieves all ledger entries for a transaction")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Ledger entries retrieved successfully",
                     content = @Content(schema = @Schema(implementation = LedgerEntry.class))),
        @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<List<LedgerEntry>> getTransactionLedgerEntries(
            @Parameter(description = "ID of the transaction") @PathVariable UUID id) {
        List<LedgerEntry> entries = doubleEntryService.getTransactionEntries(id);
        return ResponseEntity.ok(entries);
    }
} 