package com.cubeia.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

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
    @Schema(description = "ID of the sender account", example = "123e4567-e89b-12d3-a456-426614174000")
    UUID fromAccountId,
    
    @NotNull(message = "Receiver account ID is required")
    @Schema(description = "ID of the receiver account", example = "123e4567-e89b-12d3-a456-426614174001")
    UUID toAccountId,
    
    @NotNull(message = "Transfer amount is required")
    @Positive(message = "Transfer amount must be positive")
    @Schema(description = "Amount to transfer", example = "100.00")
    BigDecimal amount
) {} 