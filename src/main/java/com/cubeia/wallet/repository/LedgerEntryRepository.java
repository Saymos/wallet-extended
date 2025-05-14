package com.cubeia.wallet.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Calculates the balance for a specific account by summing all debits and credits.
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
     * Finds all ledger entries for a specific account and type.
     *
     * @param accountId the ID of the account
     * @param entryType the type of entry
     * @param pageable pagination information
     * @return a page of ledger entries
     */
    Page<LedgerEntry> findByAccountIdAndEntryTypeOrderByTimestampDesc(
            UUID accountId, EntryType entryType, Pageable pageable);
} 