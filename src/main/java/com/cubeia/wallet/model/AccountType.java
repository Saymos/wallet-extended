package com.cubeia.wallet.model;

/**
 * Sealed interface representing different types of accounts in the wallet system.
 * 
 * Uses sealed interface with singleton implementations for type safety, extensibility,
 * and pattern matching. See README's "Account Type Design" section for detailed design rationale.
 */
public sealed interface AccountType permits 
    AccountType.MainAccount,
    AccountType.BonusAccount, 
    AccountType.PendingAccount, 
    AccountType.JackpotAccount,
    AccountType.SystemAccount {
    
    /**
     * Get the string representation of this account type.
     * This is primarily used for database persistence through AccountTypeConverter.
     */
    String name();
    
    /**
     * Get the maximum withdrawal amount for this account type.
     * Used by the validation service to check if a withdrawal is allowed.
     * 
     * @return true if full balance can be withdrawn, false if no withdrawals are allowed
     */
    default boolean allowFullBalanceWithdrawal() {
        return false;
    }
    
    /**
     * Main player account for regular gameplay.
     * In a production system, this would maintain core balance and transaction history.
     */
    final class MainAccount implements AccountType {
        private MainAccount() {}
        private static final MainAccount INSTANCE = new MainAccount();
        public static MainAccount getInstance() { return INSTANCE; }
        @Override public String name() { return "MAIN"; }
        @Override public String toString() { return name(); }
        @Override public boolean allowFullBalanceWithdrawal() { return true; }
    }
    
    /**
     * Account for bonus funds with potential wagering requirements.
     * In a production system, this would track:
     * - Expiration date for bonus funds
     * - Wagering requirements before withdrawal
     * - Bonus terms and conditions
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
     * In a production system, this would track:
     * - Reason for pending status
     * - Verification requirements
     * - Time in pending state
     * - Associated documentation
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
     * In a production system, this would track:
     * - Contribution rates
     * - Jackpot accumulation rules
     * - Progressive vs. fixed jackpot information
     * - Winning distribution mechanisms
     */
    final class JackpotAccount implements AccountType {
        private JackpotAccount() {}
        private static final JackpotAccount INSTANCE = new JackpotAccount();
        public static JackpotAccount getInstance() { return INSTANCE; }
        @Override public String name() { return "JACKPOT"; }
        @Override public String toString() { return name(); }
        @Override public boolean allowFullBalanceWithdrawal() { return true; }
    }
    
    /**
     * System account for internal operations like funding test accounts.
     * In a production system, this would:
     * - Have special privileges for administrative operations
     * - Not be subject to normal withdrawal limits
     * - Be used for system-level transfers and adjustments
     * - Have full audit trail capabilities
     */
    final class SystemAccount implements AccountType {
        private SystemAccount() {}
        private static final SystemAccount INSTANCE = new SystemAccount();
        public static SystemAccount getInstance() { return INSTANCE; }
        @Override public String name() { return "SYSTEM"; }
        @Override public String toString() { return name(); }
        @Override public boolean allowFullBalanceWithdrawal() { return true; }
    }
} 