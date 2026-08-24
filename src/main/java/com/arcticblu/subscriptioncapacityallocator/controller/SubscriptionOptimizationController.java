package com.arcticblu.subscriptioncapacityallocator.controller;

import com.arcticblu.subscriptioncapacityallocator.dto.request.OptimizeRequest;
import com.arcticblu.subscriptioncapacityallocator.dto.response.AuditTrailResponse;
import com.arcticblu.subscriptioncapacityallocator.dto.response.OptimizeResponse;
import com.arcticblu.subscriptioncapacityallocator.exception.SubscriptionRunNotFoundException;
import com.arcticblu.subscriptioncapacityallocator.service.SubscriptionOptimizationService;
import jakarta.validation.Valid;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionOptimizationController {

    private final SubscriptionOptimizationService service;

    public SubscriptionOptimizationController(SubscriptionOptimizationService service) {
        this.service = service;
    }

    @PostMapping("/optimize")
    public ResponseEntity<OptimizeResponse> optimize(@Valid @RequestBody OptimizeRequest request) {
        OptimizeResponse response = service.optimize(request);
        HttpStatus status = response.acceptedSubscriptions().isEmpty() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{requestId}")
    public OptimizeResponse getById(@PathVariable UUID requestId) {
        return service.findById(requestId)
                .orElseThrow(() -> new SubscriptionRunNotFoundException(requestId));
    }
    @GetMapping
    public PagedModel<AuditTrailResponse> getAll(@PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(service.findAll(pageable));
    }
}
