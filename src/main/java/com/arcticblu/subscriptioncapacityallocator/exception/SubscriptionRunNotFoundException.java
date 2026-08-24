package com.arcticblu.subscriptioncapacityallocator.exception;

import java.util.UUID;

public class SubscriptionRunNotFoundException extends RuntimeException {
    public SubscriptionRunNotFoundException(UUID requestId) {
        super("No optimization run found with id " + requestId);
    }
}