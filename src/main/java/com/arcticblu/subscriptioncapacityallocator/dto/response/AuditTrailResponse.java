package com.arcticblu.subscriptioncapacityallocator.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditTrailResponse(
            UUID requestId,
            BigDecimal maxCapacity,
            List<SubscriptionAuditResponseDto> subscriptions,
            BigDecimal totalRequestedAmount,
            BigDecimal totalFeeRevenue,
            Instant createdAt) {}
