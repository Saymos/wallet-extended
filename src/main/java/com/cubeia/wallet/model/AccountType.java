package com.cubeia.wallet.model;

/**
 * Enum representing different types of accounts in the wallet system.
 * This is common in online gaming platforms where players might have
 * separate wallets for different purposes.
 */
public enum AccountType {
    MAIN,     // Main player account for regular gameplay
    BONUS,    // Account for bonus funds with potential wagering requirements
    PENDING,  // Account for funds in pending state (awaiting verification, etc.)
    JACKPOT   // Account for jackpot contributions/winnings
} 