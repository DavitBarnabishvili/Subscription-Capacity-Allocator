package com.arcticblu.subscriptioncapacityallocator.service;

import com.arcticblu.subscriptioncapacityallocator.algorithm.OptimalResult;
import com.arcticblu.subscriptioncapacityallocator.algorithm.Optimizer;
import com.arcticblu.subscriptioncapacityallocator.algorithm.Subscription;
import com.arcticblu.subscriptioncapacityallocator.domain.SubscriptionOptimizationRun;
import com.arcticblu.subscriptioncapacityallocator.domain.SubscriptionRequest;
import com.arcticblu.subscriptioncapacityallocator.dto.response.OptimizeResponse;
import com.arcticblu.subscriptioncapacityallocator.dto.request.OptimizeRequest;
import com.arcticblu.subscriptioncapacityallocator.dto.response.SubscriptionResponseDto;
import com.arcticblu.subscriptioncapacityallocator.repository.SubscriptionOptimizationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SubscriptionOptimizationService {

    //not injected, algorithm is stateless, has no dependencies, it's not spring specific
    private final Optimizer optimizer = new Optimizer();

    private final SubscriptionOptimizationRepository repository;

    public SubscriptionOptimizationService(SubscriptionOptimizationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public OptimizeResponse optimize(OptimizeRequest request) {

        List<Subscription> candidates = request.availableSubscriptions().stream()
                .map(dto -> new Subscription(dto.investorName(), dto.requestedAmount(), dto.feeRevenue()))
                .toList();

        OptimalResult result = optimizer.optimize(request.maxCapacity(), candidates);

        List<Subscription> accepted = result.finalSubscriptions();

        SubscriptionOptimizationRun run = new SubscriptionOptimizationRun(
                UUID.randomUUID(),
                request.maxCapacity(),
                result.totalRequestedAmount(),
                result.totalFeeRevenue(),
                Instant.now());

        for (Subscription candidate : candidates) {
            run.addSubscriptionRequest(new SubscriptionRequest(
                    candidate.investorName(),
                    candidate.requestedAmount(),
                    candidate.feeRevenue(),
                    isAccepted(accepted, candidate)));
        }

        repository.save(run);
        return toResponse(run);
    }

    // this is necessary for the worst case scenario when two distinct subscriptions hold the exact same data,
    // but are different objects. ".equals" would return true, "==" will return false.
    // if unchecked could lead to overcounting subscriptions and exceeding capacity.
    // I'll make sure to include a test case for the scenario.
    private boolean isAccepted(List<Subscription> acceptedSubscriptions, Subscription candidate) {
        for(Subscription s : acceptedSubscriptions) {
            if(s == candidate) return true;
        }

        return false;
    }

    @Transactional(readOnly = true)
    public Optional<OptimizeResponse> findById(UUID requestId) {
        return repository.findById(requestId).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<OptimizeResponse> findAll(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    private OptimizeResponse toResponse(SubscriptionOptimizationRun run) {
        List<SubscriptionResponseDto> accepted = run.getSubscriptionRequests().stream()
                .filter(SubscriptionRequest::isAccepted)
                .map(r -> new SubscriptionResponseDto(r.getInvestorName(), r.getRequestedAmount(), r.getFeeRevenue()))
                .toList();

        return new OptimizeResponse(
                run.getId(), accepted, run.getTotalRequestedAmount(), run.getTotalFeeRevenue(), run.getCreatedAt());
    }
}