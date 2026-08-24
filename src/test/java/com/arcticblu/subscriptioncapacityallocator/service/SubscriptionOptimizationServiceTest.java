package com.arcticblu.subscriptioncapacityallocator.service;

import com.arcticblu.subscriptioncapacityallocator.domain.SubscriptionOptimizationRun;
import com.arcticblu.subscriptioncapacityallocator.domain.SubscriptionRequest;
import com.arcticblu.subscriptioncapacityallocator.dto.request.OptimizeRequest;
import com.arcticblu.subscriptioncapacityallocator.dto.request.SubscriptionRequestDto;
import com.arcticblu.subscriptioncapacityallocator.dto.response.AuditTrailResponse;
import com.arcticblu.subscriptioncapacityallocator.dto.response.OptimizeResponse;
import com.arcticblu.subscriptioncapacityallocator.repository.SubscriptionOptimizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionOptimizationServiceTest {

    @Mock
    private SubscriptionOptimizationRepository repository;

    private SubscriptionOptimizationService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionOptimizationService(repository);
    }

    @Test
    void optimize_savesAuditTrail_OnlyReturnsAccepted() {
        SubscriptionRequestDto a = new SubscriptionRequestDto("Investor A", new BigDecimal("5"), new BigDecimal("120"));
        SubscriptionRequestDto b = new SubscriptionRequestDto("Investor B", new BigDecimal("10"), new BigDecimal("200"));
        SubscriptionRequestDto c = new SubscriptionRequestDto("Investor C", new BigDecimal("20"), new BigDecimal("500"));
        OptimizeRequest request = new OptimizeRequest(new BigDecimal("15"), List.of(a, b, c));

        when(repository.save(any())).thenAnswer(x -> x.getArgument(0));

        OptimizeResponse response = service.optimize(request);

        // A and B fit and beat every other combination.
        assertThat(response.acceptedSubscriptions()).hasSize(2);
        assertThat(response.totalRequestedAmount()).isEqualByComparingTo("15");
        assertThat(response.totalFeeRevenue()).isEqualByComparingTo("320");
        assertThat(response.requestId()).isNotNull();

        // when service.optimize was called repository.save() got invoked,
        // this captor captures the argument that was passed to save().
        ArgumentCaptor<SubscriptionOptimizationRun> captor = ArgumentCaptor.forClass(SubscriptionOptimizationRun.class);
        verify(repository).save(captor.capture());
        SubscriptionOptimizationRun savedRun = captor.getValue();

        //this shows every subscription was passed to the repository but only 2 with accepted=true, audit trail.
        assertThat(savedRun.getSubscriptionRequests()).hasSize(3);
        long acceptedCount = savedRun.getSubscriptionRequests().stream()
                .filter(SubscriptionRequest::isAccepted).count();
        assertThat(acceptedCount).isEqualTo(2);
    }

    @Test
    void optimize_nothingFits_savesAsRejected_ReturnsEmpty() {
        SubscriptionRequestDto a = new SubscriptionRequestDto("Investor A", new BigDecimal("50"), new BigDecimal("500"));
        OptimizeRequest request = new OptimizeRequest(new BigDecimal("10"), List.of(a));

        when(repository.save(any())).thenAnswer(x -> x.getArgument(0));

        OptimizeResponse response = service.optimize(request);

        assertThat(response.acceptedSubscriptions()).isEmpty();
        assertThat(response.totalFeeRevenue()).isEqualByComparingTo("0");

        ArgumentCaptor<SubscriptionOptimizationRun> captor = ArgumentCaptor.forClass(SubscriptionOptimizationRun.class);
        verify(repository).save(captor.capture());

        SubscriptionOptimizationRun savedRun = captor.getValue();
        assertThat(savedRun.getSubscriptionRequests()).hasSize(1);
        assertThat(savedRun.getSubscriptionRequests().getFirst().isAccepted()).isFalse();
    }

    @Test
    void optimize_identicalRequests_onlyOneAccepted() {
        // two identical requests must still be treated as two distinct requests and only one
        // that the algorithm accepts should be marked as accepted.
        SubscriptionRequestDto dto1 = new SubscriptionRequestDto("Barni", new BigDecimal("5"), new BigDecimal("100"));
        SubscriptionRequestDto dto2 = new SubscriptionRequestDto("Barni", new BigDecimal("5"), new BigDecimal("100"));
        OptimizeRequest request = new OptimizeRequest(new BigDecimal("5"), List.of(dto1, dto2));

        when(repository.save(any())).thenAnswer(x -> x.getArgument(0));

        OptimizeResponse response = service.optimize(request);

        assertThat(response.acceptedSubscriptions()).hasSize(1);

        ArgumentCaptor<SubscriptionOptimizationRun> captor = ArgumentCaptor.forClass(SubscriptionOptimizationRun.class);
        verify(repository).save(captor.capture());

        SubscriptionOptimizationRun savedRun = captor.getValue();
        assertThat(savedRun.getSubscriptionRequests()).hasSize(2);
        long acceptedCount = savedRun.getSubscriptionRequests().stream()
                .filter(SubscriptionRequest::isAccepted).count();
        assertThat(acceptedCount).isEqualTo(1);
    }

    @Test
    void findById_found_returnsOnlyAcceptedSubscriptions() {
        UUID id = UUID.randomUUID();
        SubscriptionOptimizationRun run = new SubscriptionOptimizationRun(
                id, new BigDecimal("15"), new BigDecimal("15"), new BigDecimal("320"), Instant.now());

        run.addSubscriptionRequest(new SubscriptionRequest("Investor A", new BigDecimal("5"),
                new BigDecimal("120"), true));
        run.addSubscriptionRequest(new SubscriptionRequest("Investor B", new BigDecimal("10"),
                new BigDecimal("200"), true));
        run.addSubscriptionRequest(new SubscriptionRequest("Investor C", new BigDecimal("20"),
                new BigDecimal("500"), false));

        when(repository.findById(id)).thenReturn(Optional.of(run));

        Optional<OptimizeResponse> result = service.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().acceptedSubscriptions()).hasSize(2);
        assertThat(result.get().requestId()).isEqualTo(id);
    }

    @Test
    void findById_notFound_returnsEmptyOptional() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.findById(id)).isEmpty();
    }

    @Test
    void findAll_mapsEachRunInThePageToAResponse() {
        SubscriptionOptimizationRun run1 = new SubscriptionOptimizationRun(
                UUID.randomUUID(), new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100"), Instant.now());

        run1.addSubscriptionRequest(
                new SubscriptionRequest("Investor A", new BigDecimal("10"), new BigDecimal("100"), true));

        SubscriptionOptimizationRun run2 = new SubscriptionOptimizationRun(
                UUID.randomUUID(), new BigDecimal("5"), new BigDecimal("0"), new BigDecimal("0"), Instant.now());

        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(run1, run2), pageable, 2));

        Page<AuditTrailResponse> page = service.findAll(pageable);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).subscriptions()).hasSize(1);
        assertThat(page.getContent().get(1).subscriptions()).isEmpty();
    }
}