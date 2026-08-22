package com.arcticblu.subscriptioncapacityallocator.dto.response;

import java.math.BigDecimal;

public record SubscriptionResponseDto(
        String investorName,
        BigDecimal requestedAmount,
        BigDecimal feeRevenue) {}
