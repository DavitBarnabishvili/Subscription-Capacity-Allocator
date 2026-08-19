package com.arcticblu.subscriptioncapacityallocator.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "subscription_optimization_run")
public class SubscriptionOptimizationRun {

    @Id
    private UUID id;

    @Column(name = "max_capacity", nullable = false)
    private BigDecimal maxCapacity;

    @Column(name = "total_requested_amount", nullable = false)
    private BigDecimal totalRequestedAmount;

    @Column(name = "total_fee_revenue", nullable = false)
    private BigDecimal totalFeeRevenue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubscriptionRequest> subscriptionRequests = new ArrayList<>();

    protected SubscriptionOptimizationRun() { }

    public SubscriptionOptimizationRun(UUID id, BigDecimal maxCapacity, BigDecimal totalRequestedAmount,
                                       BigDecimal totalFeeRevenue, Instant createdAt) {
        this.id = id;
        this.maxCapacity = maxCapacity;
        this.totalRequestedAmount = totalRequestedAmount;
        this.totalFeeRevenue = totalFeeRevenue;
        this.createdAt = createdAt;
    }

    public void addSubscriptionRequest(SubscriptionRequest request) {
        subscriptionRequests.add(request);
        request.setRun(this);
    }

}