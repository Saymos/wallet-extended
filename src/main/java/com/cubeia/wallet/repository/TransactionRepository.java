package com.cubeia.wallet.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cubeia.wallet.model.Transaction;

/**
 * Repository for Transaction entity operations.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    
    /**
     * Find all transactions where the specified account ID is either the sender or receiver.
     *
     * @param accountId The account ID to search for
     * @return List of transactions involving the account
     */
    @Query("SELECT t FROM Transaction t WHERE t.fromAccountId = :accountId OR t.toAccountId = :accountId ORDER BY t.timestamp DESC")
    List<Transaction> findByAccountId(@Param("accountId") UUID accountId);
    
    /**
     * Find a transaction by its reference ID.
     * 
     * @param reference The reference ID to search for
     * @return Optional containing the transaction if found, or empty if not found
     */
    Optional<Transaction> findByReference(String reference);
    
    /**
     * Find a transaction by its reference ID, ignoring case.
     * This is useful when dealing with external payment systems that may use mixed-case reference IDs.
     * 
     * @param reference The reference ID to search for
     * @return Optional containing the transaction if found, or empty if not found
     */
    @Query("SELECT t FROM Transaction t WHERE LOWER(t.reference) = LOWER(:reference)")
    Optional<Transaction> findByReferenceIgnoreCase(@Param("reference") String reference);
    
    /**
     * Find all transactions with a specific reference ID.
     * 
     * @param reference The reference ID to search for
     * @return List of transactions with the specified reference ID
     */
    List<Transaction> findAllByReference(String reference);
} 