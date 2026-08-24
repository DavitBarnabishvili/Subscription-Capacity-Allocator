package com.arcticblu.subscriptioncapacityallocator.dto.response;

import java.math.BigDecimal;

public record SubscriptionAuditResponseDto(
        String investorName,
        BigDecimal requestedAmount,
        BigDecimal feeRevenue,
        boolean accepted) {}

