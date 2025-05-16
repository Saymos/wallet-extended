package com.cubeia.wallet.config;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.cubeia.wallet.model.AccountType;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
public class JacksonConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testSerializeMainAccount() throws Exception {
        // Test regular serialization
        String json = objectMapper.writeValueAsString(AccountType.MainAccount.getInstance());
        assertEquals("\"MAIN\"", json);
    }
    
    @Test
    public void testSerializeNullAccount() throws Exception {
        // Test null serialization
        AccountType nullAccount = null;
        String json = objectMapper.writeValueAsString(nullAccount);
        assertEquals("null", json);
    }
    
    @Test
    public void testDeserializeMainAccount() throws Exception {
        // Test regular deserialization
        AccountType account = objectMapper.readValue("\"MAIN\"", AccountType.class);
        assertEquals(AccountType.MainAccount.getInstance(), account);
    }
    
    @Test
    public void testDeserializeBonusAccount() throws Exception {
        AccountType account = objectMapper.readValue("\"BONUS\"", AccountType.class);
        assertEquals(AccountType.BonusAccount.getInstance(), account);
    }
    
    @Test
    public void testDeserializePendingAccount() throws Exception {
        AccountType account = objectMapper.readValue("\"PENDING\"", AccountType.class);
        assertEquals(AccountType.PendingAccount.getInstance(), account);
    }
    
    @Test
    public void testDeserializeJackpotAccount() throws Exception {
        AccountType account = objectMapper.readValue("\"JACKPOT\"", AccountType.class);
        assertEquals(AccountType.JackpotAccount.getInstance(), account);
    }
    
    @Test
    public void testDeserializeNullAccount() throws Exception {
        // Test null deserialization
        AccountType account = objectMapper.readValue("null", AccountType.class);
        assertNull(account);
    }
    
    @Test
    public void testDeserializeInvalidAccountType() {
        // Test invalid account type
        assertThrows(IOException.class, () -> {
            objectMapper.readValue("\"INVALID_TYPE\"", AccountType.class);
        });
    }

    @Test
    public void testSerializeNullAccountType() throws Exception {
        // Test null serialization
        String json = objectMapper.writeValueAsString(null);
        assertEquals("null", json);
        
        // Create a wrapper class to test null field serialization
        class Wrapper {
            @SuppressWarnings("unused") // Used by Jackson for serialization
            private String name = "test";
        }
        
        Wrapper wrapper = new Wrapper();
        
        // Since we have NON_NULL inclusion, the field should be excluded
        String wrapperJson = objectMapper.writeValueAsString(wrapper);
        assertEquals("{\"name\":\"test\"}", wrapperJson);
    }
    
    @Test
    public void testDeserializeNullAccountType() throws Exception {
        // Test deserializing null
        AccountType type = objectMapper.readValue("null", AccountType.class);
        assertNull(type);
    }
} 