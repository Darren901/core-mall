package com.coremall.order.integration;

import com.coremall.order.dto.CreateOrderRequest;
import com.coremall.order.jpa.repository.OrderRepository;
import com.coremall.order.jpa.repository.OutboxEventRepository;
import com.coremall.order.service.OutboxRelayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("OrderIntegrationTest - Write-Behind Relay")
class OrderIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void setRedisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxRelayService relayService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanUp() {
        orderRepository.deleteAll();
        outboxEventRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("建立訂單寫入 Redis → Relay → DB 有資料 + OutboxEvent")
    void shouldRelayOrderToDbAfterCreation() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest("user-1", "Apple", 5);

        MvcResult result = mockMvc.perform(post("/internal/v1/orders")
                        .header("X-Idempotency-Key", "int-idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn();

        // DB 尚未有資料（Write-Behind 尚未 relay）
        assertThat(orderRepository.count()).isZero();

        // 觸發 relay
        relayService.relay();

        // DB 應該有 1 筆訂單 + 1 筆 outbox event
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("相同冪等鍵重複呼叫，DB 只有 1 筆訂單")
    void shouldNotDuplicateOrderWithSameIdempotencyKey() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest("user-2", "Banana", 3);

        mockMvc.perform(post("/internal/v1/orders")
                        .header("X-Idempotency-Key", "int-idem-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // 相同冪等鍵再打一次
        mockMvc.perform(post("/internal/v1/orders")
                        .header("X-Idempotency-Key", "int-idem-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        relayService.relay();

        // 只有 1 筆（冪等保護）
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("建立後可透過 GET 查詢（Redis cache）")
    void shouldGetOrderFromRedisAfterCreation() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest("user-3", "Cherry", 2);

        MvcResult created = mockMvc.perform(post("/internal/v1/orders")
                        .header("X-Idempotency-Key", "int-idem-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = created.getResponse().getContentAsString();
        String orderId = objectMapper.readTree(body).path("data").path("id").asText();

        mockMvc.perform(get("/internal/v1/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("Cherry"));
    }
}
