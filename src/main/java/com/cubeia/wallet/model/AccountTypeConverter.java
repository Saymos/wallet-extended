package com.cubeia.wallet.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter for the AccountType sealed interface hierarchy.
 * Converts between AccountType instances and their database string representation.
 */
@Converter(autoApply = true)
public class AccountTypeConverter implements AttributeConverter<AccountType, String> {

    @Override
    public String convertToDatabaseColumn(AccountType accountType) {
        if (accountType == null) {
            return null;
        }
        return accountType.name();
    }

    @Override
    public AccountType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        
        return switch(dbData) {
            case "MAIN" -> AccountType.MainAccount.getInstance();
            case "BONUS" -> AccountType.BonusAccount.getInstance();
            case "PENDING" -> AccountType.PendingAccount.getInstance();
            case "JACKPOT" -> AccountType.JackpotAccount.getInstance();
            default -> throw new IllegalArgumentException("Unknown account type: " + dbData);
        };
    }
} 