package com.cubeia.wallet.dto;

import com.cubeia.wallet.model.Currency;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for account creation requests.
 */
public record CreateAccountRequestDto(
    @Schema(description = "The currency for the account", example = "EUR")
    @NotNull
    Currency currency,
    
    @Schema(description = "The type of account (MAIN, BONUS, PENDING, JACKPOT, SYSTEM)", example = "MAIN")
    @NotNull
    String accountType
) {} 