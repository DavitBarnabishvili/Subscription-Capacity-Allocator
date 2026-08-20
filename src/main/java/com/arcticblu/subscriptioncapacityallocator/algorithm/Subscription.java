package com.arcticblu.subscriptioncapacityallocator.algorithm;

import java.math.BigDecimal;
public record Subscription(String investorName, BigDecimal requestedAmount, BigDecimal feeRevenue){}
