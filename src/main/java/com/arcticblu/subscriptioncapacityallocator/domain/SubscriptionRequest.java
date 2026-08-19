package com.arcticblu.subscriptioncapacityallocator.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "subscription_request")
public class SubscriptionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private SubscriptionOptimizationRun run;

    @Column(name = "investor_name", nullable = false)
    private String investorName;

    @Column(name = "requested_amount", nullable = false)
    private BigDecimal requestedAmount;

    @Column(name = "fee_revenue", nullable = false)
    private BigDecimal feeRevenue;

    @Column(nullable = false)
    private boolean accepted;

    protected SubscriptionRequest() { }

    public SubscriptionRequest(String investorName, BigDecimal requestedAmount, BigDecimal feeRevenue, boolean accepted) {
        this.investorName = investorName;
        this.requestedAmount = requestedAmount;
        this.feeRevenue = feeRevenue;
        this.accepted = accepted;
    }

    void setRun(SubscriptionOptimizationRun run) { this.run = run; }

}