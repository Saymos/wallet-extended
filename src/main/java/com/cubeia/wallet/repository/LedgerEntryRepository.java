package com.cubeia.wallet.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cubeia.wallet.model.Currency;
import com.cubeia.wallet.model.EntryType;
import com.cubeia.wallet.model.LedgerEntry;

/**
 * Repository for accessing and managing LedgerEntry entities.
 * <p>
 * Provides methods for querying and manipulating ledger entries in the double-entry bookkeeping system.
 * </p>
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    
    /**
     * Finds all ledger entries for a specific account, ordered by timestamp (newest first).
     *
     * @param accountId the ID of the account
     * @param pageable pagination information
     * @return a page of ledger entries
     */
    Page<LedgerEntry> findByAccountIdOrderByTimestampDesc(UUID accountId, Pageable pageable);
    
    /**
     * Finds all ledger entries for a specific account, ordered by timestamp (newest first).
     *
     * @param accountId the ID of the account
     * @return a list of ledger entries
     */
    List<LedgerEntry> findByAccountIdOrderByTimestampDesc(UUID accountId);
    
    /**
     * Finds all ledger entries for a specific transaction.
     *
     * @param transactionId the ID of the transaction
     * @return a list of ledger entries
     */
    List<LedgerEntry> findByTransactionId(UUID transactionId);
    
    /**
     * Calculates the sum of amounts for a specific account and entry type.
     *
     * @param accountId the ID of the account
     * @param entryType the type of entry (DEBIT or CREDIT)
     * @return the sum of amounts
     */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.accountId = :accountId AND e.entryType = :entryType")
    BigDecimal sumByAccountIdAndType(@Param("accountId") UUID accountId, @Param("entryType") EntryType entryType);
    
    /**
     * Calculates the balance for a specific account by summing all debits and credits across all currencies.
     * <p>
     * The balance is calculated as (sum of all CREDIT amounts) - (sum of all DEBIT amounts).
     * </p>
     *
     * @param accountId the ID of the account
     * @return the current balance
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END), 0) FROM LedgerEntry e WHERE e.accountId = :accountId")
    BigDecimal calculateBalance(@Param("accountId") UUID accountId);
    
    /**
     * Calculates the balance for a specific account by summing all debits and credits.
     * <p>
     * The balance is calculated as (sum of all CREDIT amounts) - (sum of all DEBIT amounts).
     * If currency is specified, only entries with that currency are considered.
     * </p>
     *
     * @param accountId the ID of the account
     * @param currency the currency to filter by
     * @return the current balance for the specified currency
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END), 0) FROM LedgerEntry e WHERE e.accountId = :accountId AND e.currency = :currency")
    BigDecimal calculateBalanceByCurrency(@Param("accountId") UUID accountId, @Param("currency") Currency currency);
    
    /**
     * Finds all ledger entries for a specific account and currency.
     *
     * @param accountId the ID of the account
     * @param currency the currency to filter by
     * @return a list of ledger entries
     */
    List<LedgerEntry> findByAccountIdAndCurrencyOrderByTimestampDesc(UUID accountId, Currency currency);
    
    /**
     * Finds all ledger entries for a specific transaction and currency.
     *
     * @param transactionId the ID of the transaction
     * @param currency the currency to filter by
     * @return a list of ledger entries
     */
    List<LedgerEntry> findByTransactionIdAndCurrency(UUID transactionId, Currency currency);
    
    /**
     * Finds all ledger entries for a specific account and type.
     *
     * @param accountId the ID of the account
     * @param entryType the type of entry
     * @param pageable pagination information
     * @return a page of ledger entries
     */
    Page<LedgerEntry> findByAccountIdAndEntryTypeOrderByTimestampDesc(
            UUID accountId, EntryType entryType, Pageable pageable);
            
    /**
     * Finds all ledger entries for a specific account before a given timestamp, ordered by timestamp (oldest first).
     *
     * @param accountId the ID of the account
     * @param timestamp the timestamp before which to find entries
     * @return a list of ledger entries
     */
    List<LedgerEntry> findByAccountIdAndTimestampBeforeOrderByTimestampAsc(UUID accountId, LocalDateTime timestamp);
    
    /**
     * Finds all ledger entries for a specific account between two timestamps, ordered by timestamp (oldest first).
     *
     * @param accountId the ID of the account
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return a list of ledger entries
     */
    List<LedgerEntry> findByAccountIdAndTimestampBetweenOrderByTimestampAsc(
            UUID accountId, LocalDateTime startDate, LocalDateTime endDate);
} 