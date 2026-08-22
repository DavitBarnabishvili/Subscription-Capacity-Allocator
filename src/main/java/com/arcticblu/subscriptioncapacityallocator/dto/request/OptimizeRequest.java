package com.arcticblu.subscriptioncapacityallocator.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record OptimizeRequest(
        @NotNull @PositiveOrZero BigDecimal maxCapacity,
        @NotNull List<@Valid SubscriptionRequestDto> availableSubscriptions) {}
