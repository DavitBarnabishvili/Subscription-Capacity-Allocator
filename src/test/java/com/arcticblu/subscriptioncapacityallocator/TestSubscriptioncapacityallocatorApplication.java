package com.arcticblu.subscriptioncapacityallocator;

import org.springframework.boot.SpringApplication;

public class TestSubscriptioncapacityallocatorApplication {

	public static void main(String[] args) {
		SpringApplication.from(SubscriptioncapacityallocatorApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
