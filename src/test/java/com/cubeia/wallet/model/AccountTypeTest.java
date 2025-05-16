package com.cubeia.wallet.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;

class AccountTypeTest {

    @Test
    public void testMainAccountSingleton() {
        AccountType.MainAccount instance1 = AccountType.MainAccount.getInstance();
        AccountType.MainAccount instance2 = AccountType.MainAccount.getInstance();
        
        assertSame(instance1, instance2, "MainAccount should return the same instance");
        assertEquals("MAIN", instance1.name());
        assertEquals("MAIN", instance1.toString());
    }
    
    @Test
    public void testBonusAccountSingleton() {
        AccountType.BonusAccount instance1 = AccountType.BonusAccount.getInstance();
        AccountType.BonusAccount instance2 = AccountType.BonusAccount.getInstance();
        
        assertSame(instance1, instance2, "BonusAccount should return the same instance");
        assertEquals("BONUS", instance1.name());
        assertEquals("BONUS", instance1.toString());
    }
    
    @Test
    public void testPendingAccountSingleton() {
        AccountType.PendingAccount instance1 = AccountType.PendingAccount.getInstance();
        AccountType.PendingAccount instance2 = AccountType.PendingAccount.getInstance();
        
        assertSame(instance1, instance2, "PendingAccount should return the same instance");
        assertEquals("PENDING", instance1.name());
        assertEquals("PENDING", instance1.toString());
    }
    
    @Test
    public void testJackpotAccountSingleton() {
        AccountType.JackpotAccount instance1 = AccountType.JackpotAccount.getInstance();
        AccountType.JackpotAccount instance2 = AccountType.JackpotAccount.getInstance();
        
        assertSame(instance1, instance2, "JackpotAccount should return the same instance");
        assertEquals("JACKPOT", instance1.name());
        assertEquals("JACKPOT", instance1.toString());
    }
} 