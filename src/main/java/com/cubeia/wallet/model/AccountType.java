package com.cubeia.wallet.model;

/**
 * Sealed interface representing different types of accounts in the wallet system.
 * Each account type can have specific rules and behaviors.
 */
public sealed interface AccountType permits 
    AccountType.MainAccount,
    AccountType.BonusAccount, 
    AccountType.PendingAccount, 
    AccountType.JackpotAccount {
    
    /**
     * Get the string representation of this account type.
     * This is primarily used for database persistence.
     */
    String name();
    
    /**
     * Main player account for regular gameplay.
     */
    final class MainAccount implements AccountType {
        private MainAccount() {}
        private static final MainAccount INSTANCE = new MainAccount();
        public static MainAccount getInstance() { return INSTANCE; }
        @Override public String name() { return "MAIN"; }
        @Override public String toString() { return name(); }
    }
    
    /**
     * Account for bonus funds with potential wagering requirements.
     */
    final class BonusAccount implements AccountType {
        private BonusAccount() {}
        private static final BonusAccount INSTANCE = new BonusAccount();
        public static BonusAccount getInstance() { return INSTANCE; }
        @Override public String name() { return "BONUS"; }
        @Override public String toString() { return name(); }
    }
    
    /**
     * Account for funds in pending state (awaiting verification, etc.)
     */
    final class PendingAccount implements AccountType {
        private PendingAccount() {}
        private static final PendingAccount INSTANCE = new PendingAccount();
        public static PendingAccount getInstance() { return INSTANCE; }
        @Override public String name() { return "PENDING"; }
        @Override public String toString() { return name(); }
    }
    
    /**
     * Account for jackpot contributions/winnings.
     */
    final class JackpotAccount implements AccountType {
        private JackpotAccount() {}
        private static final JackpotAccount INSTANCE = new JackpotAccount();
        public static JackpotAccount getInstance() { return INSTANCE; }
        @Override public String name() { return "JACKPOT"; }
        @Override public String toString() { return name(); }
    }
} 