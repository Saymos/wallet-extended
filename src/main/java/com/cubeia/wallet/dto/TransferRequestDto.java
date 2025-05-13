package com.cubeia.wallet.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * DTO for transfer requests between accounts.
 */
@Schema(description = "Transfer request data")
public class TransferRequestDto {
    
    @NotNull(message = "Sender account ID is required")
    @Schema(description = "ID of the sender account", example = "1")
    private Long fromAccountId;
    
    @NotNull(message = "Receiver account ID is required")
    @Schema(description = "ID of the receiver account", example = "2")
    private Long toAccountId;
    
    @NotNull(message = "Transfer amount is required")
    @Positive(message = "Transfer amount must be positive")
    @Schema(description = "Amount to transfer", example = "100.00")
    private BigDecimal amount;

    public Long getFromAccountId() {
        return fromAccountId;
    }

    public void setFromAccountId(Long fromAccountId) {
        this.fromAccountId = fromAccountId;
    }

    public Long getToAccountId() {
        return toAccountId;
    }

    public void setToAccountId(Long toAccountId) {
        this.toAccountId = toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
} 