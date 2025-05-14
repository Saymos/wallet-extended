package com.cubeia.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.LedgerEntry;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.model.TransactionType;

/**
 * DTO for providing transaction history with associated ledger entries.
 */
public record TransactionHistoryDTO(
    UUID transactionId,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,
    Currency currency,
    TransactionType type,
    LocalDateTime timestamp,
    String referenceId,
    List<LedgerEntryDTO> ledgerEntries
) {
    /**
     * Factory method to create a TransactionHistoryDTO from a Transaction and its LedgerEntries.
     * 
     * @param transaction The transaction
     * @param ledgerEntries The ledger entries associated with the transaction
     * @return A new TransactionHistoryDTO
     */
    public static TransactionHistoryDTO from(Transaction transaction, List<LedgerEntry> ledgerEntries) {
        return new TransactionHistoryDTO(
            transaction.getId(),
            transaction.getFromAccountId(),
            transaction.getToAccountId(),
            transaction.getAmount(),
            transaction.getCurrency(),
            transaction.getTransactionType(),
            transaction.getTimestamp(),
            transaction.getReference(),
            ledgerEntries.stream().map(LedgerEntryDTO::from).toList()
        );
    }
    
    /**
     * DTO for ledger entry details to be included in transaction history.
     */
    public record LedgerEntryDTO(
        UUID id,
        UUID accountId,
        String entryType,
        BigDecimal amount,
        LocalDateTime timestamp,
        String description
    ) {
        /**
         * Factory method to create a LedgerEntryDTO from a LedgerEntry.
         * 
         * @param entry The ledger entry
         * @return A new LedgerEntryDTO
         */
        public static LedgerEntryDTO from(LedgerEntry entry) {
            return new LedgerEntryDTO(
                entry.getId(),
                entry.getAccountId(),
                entry.getEntryType().name(),
                entry.getAmount(),
                entry.getTimestamp(),
                entry.getDescription()
            );
        }
    }
} 