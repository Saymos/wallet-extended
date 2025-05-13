package com.cubeia.wallet.model;

/**
 * Enum representing different types of transactions in the wallet system.
 * This allows for better categorization and reporting of financial movements.
 */
public enum TransactionType {
    DEPOSIT,      // Money added to the wallet from external source
    WITHDRAWAL,   // Money withdrawn from wallet to external destination
    TRANSFER,     // Transfer between accounts
    GAME_BET,     // Bet placed on a game
    GAME_WIN,     // Win from a game
    BONUS_AWARD,  // Bonus funds awarded
    JACKPOT_WIN   // Jackpot win
} 