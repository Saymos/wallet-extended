package com.cubeia.wallet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.cubeia.wallet.model.Currency;

/**
 * Exception thrown when there is a currency mismatch between accounts or 
 * between an account and a transaction.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CurrencyMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    private static final String CURRENCY_MISMATCH_TEMPLATE = "Currency mismatch: Expected %s, but got %s";
    private static final String TRANSFER_CURRENCY_MISMATCH_TEMPLATE = 
            "Cannot transfer between accounts with different currencies (%s and %s)";
    private static final String TRANSACTION_ACCOUNTS_MISMATCH_TEMPLATE = 
            "Transaction and accounts must use the same currency. "
            + "Transaction currency: %s, From account currency: %s, To account currency: %s";

    /**
     * Constructs a new exception with the specified currencies.
     * 
     * @param expected The expected currency
     * @param actual The actual currency
     */
    public CurrencyMismatchException(Currency expected, Currency actual) {
        super(String.format(CURRENCY_MISMATCH_TEMPLATE, expected, actual));
    }
    
    /**
     * Constructs a new exception for transfers between accounts with different currencies.
     * 
     * @param fromCurrency The source account currency
     * @param toCurrency The destination account currency
     * @return A new exception instance
     */
    public static CurrencyMismatchException forTransfer(Currency fromCurrency, Currency toCurrency) {
        return new CurrencyMismatchException(
                String.format(TRANSFER_CURRENCY_MISMATCH_TEMPLATE, fromCurrency, toCurrency));
    }
    
    /**
     * Constructs a new exception for currency mismatch between transaction and accounts.
     * 
     * @param txCurrency The transaction currency
     * @param fromCurrency The source account currency
     * @param toCurrency The destination account currency
     * @return A new exception instance
     */
    public static CurrencyMismatchException forTransactionAndAccounts(
            Currency txCurrency, Currency fromCurrency, Currency toCurrency) {
        return new CurrencyMismatchException(
                String.format(TRANSACTION_ACCOUNTS_MISMATCH_TEMPLATE, txCurrency, fromCurrency, toCurrency));
    }

    /**
     * Constructs a new exception with a fully formatted message.
     * 
     * @param message the detail message
     */
    public CurrencyMismatchException(String message) {
        super(message);
    }
} 