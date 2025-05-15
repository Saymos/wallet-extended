package com.cubeia.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Data Transfer Object for transfer requests.
 * Implemented as a record for immutability and conciseness.
 * Validation is handled by ValidationService instead of annotations.
 */
@Schema(description = "Transfer request data")
public record TransferRequestDto(
    @Schema(description = "ID of the sender account", example = "123e4567-e89b-12d3-a456-426614174000")
    UUID fromAccountId,
    
    @Schema(description = "ID of the receiver account", example = "123e4567-e89b-12d3-a456-426614174001")
    UUID toAccountId,
    
    @Schema(description = "Amount to transfer", example = "100.00")
    BigDecimal amount,
    
    @Schema(description = "Optional reference ID for idempotent requests", example = "REF-123456")
    String referenceId
) {} 