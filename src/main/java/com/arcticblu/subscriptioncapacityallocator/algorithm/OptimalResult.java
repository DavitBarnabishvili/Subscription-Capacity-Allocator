package com.arcticblu.subscriptioncapacityallocator.algorithm;

import java.math.BigDecimal;
import java.util.List;

public record OptimalResult (List<Subscription> finalSubscriptions,
                             BigDecimal totalRequestedAmount,
                             BigDecimal totalFeeRevenue) {}