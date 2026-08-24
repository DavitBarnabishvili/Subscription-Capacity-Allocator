package com.arcticblu.subscriptioncapacityallocator.controller;

import com.arcticblu.subscriptioncapacityallocator.dto.request.OptimizeRequest;
import com.arcticblu.subscriptioncapacityallocator.dto.request.SubscriptionRequestDto;
import com.arcticblu.subscriptioncapacityallocator.dto.response.OptimizeResponse;
import com.arcticblu.subscriptioncapacityallocator.repository.SubscriptionOptimizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class SubscriptionOptimizationIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubscriptionOptimizationRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void optimize_persists_returns201WithAcceptedSubscriptions() throws Exception {
        OptimizeRequest request = new OptimizeRequest(new BigDecimal("15"), List.of(
                new SubscriptionRequestDto("Investor A", new BigDecimal("5"), new BigDecimal("120")),
                new SubscriptionRequestDto("Investor B", new BigDecimal("10"), new BigDecimal("200")),
                new SubscriptionRequestDto("Investor C", new BigDecimal("3"), new BigDecimal("80")),
                new SubscriptionRequestDto("Investor D", new BigDecimal("8"), new BigDecimal("160"))));

        mockMvc.perform(post("/api/v1/subscriptions/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.acceptedSubscriptions.length()").value(2))
                .andExpect(jsonPath("$.totalRequestedAmount").value(15))
                .andExpect(jsonPath("$.totalFeeRevenue").value(320));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void optimize_nothingFits_persistsAuditRecord_Returns200() throws Exception {
        OptimizeRequest request = new OptimizeRequest(new BigDecimal("5"), List.of(
                new SubscriptionRequestDto("Investor A", new BigDecimal("50"), new BigDecimal("500"))));

        mockMvc.perform(post("/api/v1/subscriptions/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedSubscriptions").isEmpty())
                .andExpect(jsonPath("$.totalFeeRevenue").value(0));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void optimize_invalidInput_PersistsNothing_returns400() throws Exception {
        OptimizeRequest request = new OptimizeRequest(new BigDecimal("-5"), List.of());

        mockMvc.perform(post("/api/v1/subscriptions/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        assertThat(repository.count()).isZero();
    }

    @Test
    void getById_returnsPersistedRun() throws Exception {
        OptimizeRequest request = new OptimizeRequest(new BigDecimal("10"), List.of(
                new SubscriptionRequestDto("Investor A", new BigDecimal("10"), new BigDecimal("100"))));

        String response = mockMvc.perform(post("/api/v1/subscriptions/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID requestId = objectMapper.readValue(response, OptimizeResponse.class).requestId();

        mockMvc.perform(get("/api/v1/subscriptions/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.totalFeeRevenue").value(100));
    }

    @Test
    void getById_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getAll_returnsNewestFirst() throws Exception {
        OptimizeRequest older = new OptimizeRequest(new BigDecimal("10"), List.of(
                new SubscriptionRequestDto("Older", new BigDecimal("10"), new BigDecimal("100"))));
        OptimizeRequest newer = new OptimizeRequest(new BigDecimal("20"), List.of(
                new SubscriptionRequestDto("Newer", new BigDecimal("20"), new BigDecimal("200"))));

        String olderResponse = mockMvc.perform(post("/api/v1/subscriptions/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(older)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String newerResponse = mockMvc.perform(post("/api/v1/subscriptions/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newer)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID olderId = objectMapper.readValue(olderResponse, OptimizeResponse.class).requestId();
        UUID newerId = objectMapper.readValue(newerResponse, OptimizeResponse.class).requestId();

        mockMvc.perform(get("/api/v1/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].requestId").value(newerId.toString()))
                .andExpect(jsonPath("$.content[1].requestId").value(olderId.toString()));
    }
}