package com.arcticblu.subscriptioncapacityallocator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record SubscriptionRequestDto(
        @NotBlank String investorName,
        @NotNull @PositiveOrZero BigDecimal requestedAmount,
        @NotNull @PositiveOrZero BigDecimal feeRevenue) {}
