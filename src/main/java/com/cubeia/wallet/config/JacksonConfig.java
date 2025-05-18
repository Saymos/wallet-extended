package com.cubeia.wallet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.cubeia.wallet.model.AccountType;

import java.io.IOException;

/**
 * Jackson configuration for proper serialization of Java 21 features.
 */
@Configuration
public class JacksonConfig {

    /**
     * Customizes the Jackson ObjectMapper for the application.
     */
    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.build()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .registerModule(new JavaTimeModule())
                .registerModule(createAccountTypeModule());
    }
    
    private SimpleModule createAccountTypeModule() {
        SimpleModule accountTypeModule = new SimpleModule();
        accountTypeModule.addSerializer(AccountType.class, new JsonSerializer<AccountType>() {
            @Override
            public void serialize(AccountType value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                if (value == null) {
                    gen.writeNull();
                } else {
                    gen.writeString(value.name());
                }
            }
        });
        
        accountTypeModule.addDeserializer(AccountType.class, new JsonDeserializer<AccountType>() {
            @Override
            public AccountType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String value = p.getValueAsString();
                if (value == null) {
                    return null;
                }
                
                return switch(value) {
                    case "MAIN" -> AccountType.MainAccount.getInstance();
                    case "BONUS" -> AccountType.BonusAccount.getInstance();
                    case "PENDING" -> AccountType.PendingAccount.getInstance();
                    case "JACKPOT" -> AccountType.JackpotAccount.getInstance();
                    default -> throw new IOException("Unknown account type: " + value);
                };
            }
        });
        
        return accountTypeModule;
    }
} 