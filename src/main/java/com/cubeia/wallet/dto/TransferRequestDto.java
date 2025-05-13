package com.cubeia.wallet.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * DTO for transfer requests between accounts.
 * Implemented as an immutable record using Java 21 features.
 */
@Schema(description = "Transfer request data")
public record TransferRequestDto(
    @NotNull(message = "Sender account ID is required")
    @Schema(description = "ID of the sender account", example = "1")
    Long fromAccountId,
    
    @NotNull(message = "Receiver account ID is required")
    @Schema(description = "ID of the receiver account", example = "2")
    Long toAccountId,
    
    @NotNull(message = "Transfer amount is required")
    @Positive(message = "Transfer amount must be positive")
    @Schema(description = "Amount to transfer", example = "100.00")
    BigDecimal amount
) {} 