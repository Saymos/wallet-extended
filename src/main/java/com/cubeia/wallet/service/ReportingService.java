package com.cubeia.wallet.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cubeia.wallet.dto.AccountLedgerDTO;
import com.cubeia.wallet.dto.AccountStatementDTO;
import com.cubeia.wallet.dto.TransactionHistoryDTO;
import com.cubeia.wallet.exception.AccountNotFoundException;
import com.cubeia.wallet.exception.TransactionNotFoundException;
import com.cubeia.wallet.model.Account;
import com.cubeia.wallet.model.LedgerEntry;
import com.cubeia.wallet.model.Transaction;
import com.cubeia.wallet.repository.AccountRepository;
import com.cubeia.wallet.repository.LedgerEntryRepository;
import com.cubeia.wallet.repository.TransactionRepository;

/**
 * Service for generating reports related to transactions and account activity.
 */
@Service
public class ReportingService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public ReportingService(AccountRepository accountRepository, 
            TransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }
    
    /**
     * Gets detailed transaction history with associated ledger entries.
     *
     * @param transactionId The ID of the transaction
     * @return Transaction history with ledger entry details
     * @throws TransactionNotFoundException if transaction not found
     */
    @Transactional(readOnly = true)
    public TransactionHistoryDTO getTransactionHistory(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
        
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(transactionId);
        
        return TransactionHistoryDTO.from(transaction, entries);
    }
    
    /**
     * Gets a paginated ledger for an account, showing all entries with running balance.
     *
     * @param accountId The ID of the account
     * @param pageSize The number of entries per page
     * @param pageNumber The page number (0-based)
     * @return Account ledger with running balances
     * @throws AccountNotFoundException if account not found
     */
    @Transactional(readOnly = true)
    public AccountLedgerDTO getAccountLedger(UUID accountId, int pageSize, int pageNumber) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        
        // Create pageable request
        Pageable pageable = PageRequest.of(
                pageNumber, 
                pageSize, 
                Sort.by(Sort.Direction.DESC, "timestamp"));
        
        // Get paginated entries
        List<LedgerEntry> entries = ledgerEntryRepository
                .findByAccountIdOrderByTimestampDesc(accountId, pageable)
                .getContent();
        
        // For accurate running balance, we need all entries before the page
        // Get earliest timestamp from the current page
        LocalDateTime earliestInPage = entries.isEmpty() ? LocalDateTime.now() 
                : entries.get(entries.size() - 1).getTimestamp();
        
        // Query for all entries before the earliest in the page
        List<LedgerEntry> entriesBefore = ledgerEntryRepository
                .findByAccountIdAndTimestampBeforeOrderByTimestampAsc(accountId, earliestInPage);
        
        // Create a new modifiable list to avoid UnsupportedOperationException
        List<LedgerEntry> allEntries = new ArrayList<>(entriesBefore);
        allEntries.addAll(entries);
        
        // Sort all entries chronologically for correct balance calculation
        List<LedgerEntry> sortedEntries = allEntries.stream()
                .sorted((e1, e2) -> e1.getTimestamp().compareTo(e2.getTimestamp()))
                .collect(Collectors.toList());
        
        return AccountLedgerDTO.from(accountId, account.getCurrency(), sortedEntries);
    }
    
    /**
     * Gets a statement for an account over a specified time period.
     *
     * @param accountId The ID of the account
     * @param startDate The start date for the statement
     * @param endDate The end date for the statement
     * @return Account statement for the period
     * @throws AccountNotFoundException if account not found
     */
    @Transactional(readOnly = true)
    public AccountStatementDTO getAccountStatement(UUID accountId, LocalDateTime startDate, LocalDateTime endDate) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        
        // Get entries before start date (for opening balance)
        List<LedgerEntry> entriesBeforeStart = ledgerEntryRepository
                .findByAccountIdAndTimestampBeforeOrderByTimestampAsc(accountId, startDate);
        
        // Get entries in the period
        List<LedgerEntry> entriesInPeriod = ledgerEntryRepository
                .findByAccountIdAndTimestampBetweenOrderByTimestampAsc(accountId, startDate, endDate);
        
        return AccountStatementDTO.from(
                accountId, 
                account.getCurrency(), 
                startDate, 
                endDate, 
                entriesBeforeStart, 
                entriesInPeriod);
    }
} 