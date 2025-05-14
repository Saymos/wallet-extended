package com.cubeia.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.EntryType;
import com.cubeia.wallet.model.LedgerEntry;

/**
 * DTO for providing a ledger view of an account showing all entries with running balance.
 */
public record AccountLedgerDTO(
    UUID accountId,
    Currency currency,
    BigDecimal currentBalance,
    List<LedgerEntryWithBalanceDTO> entries
) {
    /**
     * DTO for ledger entry with running balance.
     */
    public record LedgerEntryWithBalanceDTO(
        UUID id,
        UUID transactionId,
        EntryType entryType,
        BigDecimal amount,
        LocalDateTime timestamp,
        String description,
        BigDecimal runningBalance
    ) {
        /**
         * Factory method to create a LedgerEntryWithBalanceDTO from a LedgerEntry and running balance.
         * 
         * @param entry The ledger entry
         * @param runningBalance The calculated running balance after this entry
         * @return A new LedgerEntryWithBalanceDTO
         */
        public static LedgerEntryWithBalanceDTO from(LedgerEntry entry, BigDecimal runningBalance) {
            return new LedgerEntryWithBalanceDTO(
                entry.getId(),
                entry.getTransactionId(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getTimestamp(),
                entry.getDescription(),
                runningBalance
            );
        }
    }
    
    /**
     * Factory method to create an AccountLedgerDTO with calculated running balances.
     * 
     * @param accountId The account ID
     * @param currency The account currency
     * @param entries The ledger entries for the account (should be ordered by timestamp)
     * @return A new AccountLedgerDTO with calculated running balances
     */
    public static AccountLedgerDTO from(UUID accountId, Currency currency, List<LedgerEntry> entries) {
        List<LedgerEntryWithBalanceDTO> entriesWithBalance = new ArrayList<>();
        BigDecimal runningBalance = BigDecimal.ZERO;
        
        // Calculate running balance for each entry
        for (LedgerEntry entry : entries) {
            runningBalance = runningBalance.add(entry.getSignedAmount());
            entriesWithBalance.add(LedgerEntryWithBalanceDTO.from(entry, runningBalance));
        }
        
        return new AccountLedgerDTO(
            accountId,
            currency,
            runningBalance, // Final balance
            entriesWithBalance
        );
    }
} 