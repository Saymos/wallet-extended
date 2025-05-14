package com.cubeia.wallet.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cubeia.wallet.dto.AccountLedgerDTO;
import com.cubeia.wallet.dto.AccountStatementDTO;
import com.cubeia.wallet.dto.TransactionHistoryDTO;
import com.cubeia.wallet.service.ReportingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST API controller for wallet reporting operations.
 */
@RestController
@RequestMapping("/reports")
@Tag(name = "Reporting API", description = "API endpoints for transaction and account reporting")
public class ReportingController {

    private final ReportingService reportingService;
    
    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }
    
    /**
     * Gets transaction history with associated ledger entries.
     *
     * @param transactionId The ID of the transaction
     * @return Transaction history with ledger entry details
     */
    @GetMapping("/transactions/{transactionId}")
    @Operation(summary = "Get transaction history", 
               description = "Retrieves detailed transaction history with associated ledger entries")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Transaction history retrieved successfully",
                     content = @Content(schema = @Schema(implementation = TransactionHistoryDTO.class))),
        @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<TransactionHistoryDTO> getTransactionHistory(
            @Parameter(description = "ID of the transaction") @PathVariable UUID transactionId) {
        
        TransactionHistoryDTO history = reportingService.getTransactionHistory(transactionId);
        return ResponseEntity.ok(history);
    }
    
    /**
     * Gets a paginated ledger for an account, showing all entries with running balance.
     *
     * @param accountId The ID of the account
     * @param pageSize The number of entries per page (default 20)
     * @param pageNumber The page number (0-based, default 0)
     * @return Account ledger with running balances
     */
    @GetMapping("/accounts/{accountId}/ledger")
    @Operation(summary = "Get account ledger", 
               description = "Retrieves a paginated ledger for an account, showing all entries with running balance")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Account ledger retrieved successfully",
                     content = @Content(schema = @Schema(implementation = AccountLedgerDTO.class))),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<AccountLedgerDTO> getAccountLedger(
            @Parameter(description = "ID of the account") @PathVariable UUID accountId,
            @Parameter(description = "Number of entries per page (default 20)") 
                @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "Page number (0-based, default 0)") 
                @RequestParam(defaultValue = "0") int pageNumber) {
        
        AccountLedgerDTO ledger = reportingService.getAccountLedger(accountId, pageSize, pageNumber);
        return ResponseEntity.ok(ledger);
    }
    
    /**
     * Gets a statement for an account over a specified time period.
     *
     * @param accountId The ID of the account
     * @param startDate The start date for the statement
     * @param endDate The end date for the statement
     * @return Account statement for the period
     */
    @GetMapping("/accounts/{accountId}/statement")
    @Operation(summary = "Get account statement", 
               description = "Retrieves a statement for an account over a specified time period")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Account statement retrieved successfully",
                     content = @Content(schema = @Schema(implementation = AccountStatementDTO.class))),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<AccountStatementDTO> getAccountStatement(
            @Parameter(description = "ID of the account") @PathVariable UUID accountId,
            @Parameter(description = "Start date (ISO format)") 
                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)") 
                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        AccountStatementDTO statement = reportingService.getAccountStatement(accountId, startDate, endDate);
        return ResponseEntity.ok(statement);
    }
} 