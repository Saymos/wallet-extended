package com.cubeia.wallet.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for balance responses.
 * Uses Java 21 record feature to create an immutable data class.
 */
@Schema(description = "Account balance response")
public record BalanceResponseDto(
    @Schema(description = "Current balance of the account", example = "100.00")
    BigDecimal balance
) {} 