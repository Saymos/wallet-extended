package com.cubeia.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.EntryType;
import com.cubeia.wallet.model.LedgerEntry;

/**
 * DTO for providing a summary of account activity over a specified time period.
 */
public record AccountStatementDTO(
    UUID accountId,
    Currency currency,
    LocalDateTime startDate,
    LocalDateTime endDate,
    BigDecimal openingBalance,
    BigDecimal closingBalance,
    BigDecimal totalDebits,
    BigDecimal totalCredits,
    int totalTransactions,
    List<TransactionSummaryDTO> transactions
) {
    /**
     * DTO for a transaction summary in an account statement.
     */
    public record TransactionSummaryDTO(
        UUID transactionId,
        LocalDateTime timestamp,
        String description,
        BigDecimal amount,
        boolean isCredit
    ) {
        /**
         * Factory method to create a TransactionSummaryDTO from a ledger entry.
         * 
         * @param entry The ledger entry
         * @return A new TransactionSummaryDTO
         */
        public static TransactionSummaryDTO from(LedgerEntry entry) {
            return new TransactionSummaryDTO(
                entry.getTransactionId(),
                entry.getTimestamp(),
                entry.getDescription(),
                entry.getAmount(),
                entry.getEntryType() == EntryType.CREDIT
            );
        }
    }
    
    /**
     * Factory method to create an AccountStatementDTO from account details and entries in a time period.
     * 
     * @param accountId The account ID
     * @param currency The account currency
     * @param startDate The start date of the statement period
     * @param endDate The end date of the statement period
     * @param entriesBeforeStart Entries before the start date (for opening balance)
     * @param entriesInPeriod Entries in the statement period
     * @return A new AccountStatementDTO
     */
    public static AccountStatementDTO from(
            UUID accountId, 
            Currency currency,
            LocalDateTime startDate,
            LocalDateTime endDate,
            List<LedgerEntry> entriesBeforeStart,
            List<LedgerEntry> entriesInPeriod) {
        
        // Calculate opening balance (sum of all entries before start date)
        BigDecimal openingBalance = entriesBeforeStart.stream()
                .map(LedgerEntry::getSignedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate totals for the period
        BigDecimal totalDebits = entriesInPeriod.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalCredits = entriesInPeriod.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate closing balance
        BigDecimal closingBalance = openingBalance;
        for (LedgerEntry entry : entriesInPeriod) {
            closingBalance = closingBalance.add(entry.getSignedAmount());
        }
        
        // Get unique transaction IDs to count transactions
        long uniqueTransactions = entriesInPeriod.stream()
                .map(LedgerEntry::getTransactionId)
                .distinct()
                .count();
        
        // Create transaction summaries
        List<TransactionSummaryDTO> transactions = entriesInPeriod.stream()
                .map(TransactionSummaryDTO::from)
                .toList();
        
        return new AccountStatementDTO(
            accountId,
            currency,
            startDate,
            endDate,
            openingBalance,
            closingBalance,
            totalDebits,
            totalCredits,
            (int) uniqueTransactions,
            transactions
        );
    }
} 