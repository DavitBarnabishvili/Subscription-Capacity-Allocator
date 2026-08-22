package com.arcticblu.subscriptioncapacityallocator.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OptimizeResponse(
        UUID requestId,
        List<SubscriptionResponseDto> acceptedSubscriptions,
        BigDecimal totalRequestedAmount,
        BigDecimal totalFeeRevenue,
        Instant createdAt) {}
