package com.arcticblu.subscriptioncapacityallocator.repository;

import com.arcticblu.subscriptioncapacityallocator.domain.SubscriptionOptimizationRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SubscriptionOptimizationRepository extends JpaRepository<SubscriptionOptimizationRun, UUID> {
    Page<SubscriptionOptimizationRun> findAllByOrderByCreatedAtDesc(Pageable pageable);
}