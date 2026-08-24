package com.arcticblu.subscriptioncapacityallocator.controller;

import com.arcticblu.subscriptioncapacityallocator.dto.request.OptimizeRequest;
import com.arcticblu.subscriptioncapacityallocator.dto.request.SubscriptionRequestDto;
import com.arcticblu.subscriptioncapacityallocator.dto.response.AuditTrailResponse;
import com.arcticblu.subscriptioncapacityallocator.dto.response.OptimizeResponse;
import com.arcticblu.subscriptioncapacityallocator.dto.response.SubscriptionResponseDto;
import com.arcticblu.subscriptioncapacityallocator.service.SubscriptionOptimizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(SubscriptionOptimizationController.class)
class SubscriptionOptimizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SubscriptionOptimizationService service;

    @Test
    void optimize_acceptedSubscriptionsPresent_returns201() throws Exception {
        OptimizeResponse response = new OptimizeResponse(
                UUID.randomUUID(),
                List.of(new SubscriptionResponseDto("Investor A", new BigDecimal("5"), new BigDecimal("120"))),
                new BigDecimal("5"),
                new BigDecimal("120"),
                Instant.now());

        when(service.optimize(any())).thenReturn(response);

        OptimizeRequest request = new OptimizeRequest(new BigDecimal("15"), List.of(
                new SubscriptionRequestDto("Investor A", new BigDecimal("5"), new BigDecimal("120"))));

        mockMvc.perform(post("/api/v1/subscriptions/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedSubscriptions").isNotEmpty())
                .andExpect(jsonPath("$.totalFeeRevenue").value(120));
    }

    @Test
    void optimize_nothingAccepted_returns200() throws Exception {
        OptimizeResponse response = new OptimizeResponse(
                UUID.randomUUID(), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, Instant.now());

        when(service.optimize(any())).thenReturn(response);

        OptimizeRequest request = new OptimizeRequest(new BigDecimal("5"), List.of(
                new SubscriptionRequestDto("Investor A", new BigDecimal("50"), new BigDecimal("500"))));

        mockMvc.perform(post("/api/v1/subscriptions/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedSubscriptions").isEmpty());
    }

    @Test
    void optimize_negativeValues_returns400_DoesNotCallService() throws Exception {
        OptimizeRequest request = new OptimizeRequest(new BigDecimal("-5"), List.of(
                new SubscriptionRequestDto("Investor A", new BigDecimal("-20"), new BigDecimal("-10"))));

        mockMvc.perform(post("/api/v1/subscriptions/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("maxCapacity")))
                .andExpect(jsonPath("$.message", containsString("requestedAmount")))
                .andExpect(jsonPath("$.message", containsString("feeRevenue")))
                .andExpect(jsonPath("$.message", containsString("requestedAmount")))
                .andExpect(jsonPath("$.message", containsString("must be greater than or equal to 0")));

        verifyNoInteractions(service);
    }

    @Test
    void optimize_malformedRequest_returns400_DoesNotCallService() throws Exception {
        //had to use raw JSON because request wouldn't be created with malformed JSON
        String requestBody = """
                {
                  "maxCapacity": =5,
                  "availableSubscriptions": [
                    {"investorName": "Investor A", "requestedAmount": 10, "feeRevenue": 100}
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/subscriptions/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Malformed request body")));

        verifyNoInteractions(service);
    }

    @Test
    void optimize_serviceRejectsBadPrecision_returns400() throws Exception {
        when(service.optimize(any()))
                .thenThrow(new IllegalArgumentException(
                        "maxCapacity cannot have more than 4 decimal places or exceed the range of a long when scaled"));

        OptimizeRequest request = new OptimizeRequest(new BigDecimal("10.00005"), List.of());

        mockMvc.perform(post("/api/v1/subscriptions/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "maxCapacity cannot have more than 4 decimal places or exceed the range of a long when scaled"));
    }

    @Test
    void getById_found_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        OptimizeResponse response = new OptimizeResponse(id, List.of(), BigDecimal.ZERO, BigDecimal.ZERO, Instant.now());

        when(service.findById(id)).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/v1/subscriptions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(id.toString()));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/subscriptions/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No optimization run found with id " + id));
    }

    @Test
    void getAll_returnsPagedContent() throws Exception {
        AuditTrailResponse response = new AuditTrailResponse(
                UUID.randomUUID(), BigDecimal.ZERO, List.of(), BigDecimal.ZERO, BigDecimal.ZERO, Instant.now());

        when(service.findAll(any())).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void getAll_returnsPagedContent_returns200_evenWhenNoContentIsPresent() throws Exception {
        when(service.findAll(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }
}