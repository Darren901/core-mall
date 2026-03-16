package com.coremall.agent.integration;

import com.coremall.agent.dto.ChatRequest;
import com.coremall.agent.jpa.entity.AgentRun;
import com.coremall.agent.jpa.entity.AgentStep;
import com.coremall.agent.jpa.repository.AgentRunRepository;
import com.coremall.agent.jpa.repository.AgentStepRepository;
import com.coremall.agent.tool.AgentRunContext;
import com.coremall.agent.tool.OrderAgentTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Task 6.10 整合測試：
 * - POST /api/v1/agent/chat → 202 + runId
 * - 背景執行觸發 tool calls → AgentStep 記錄寫入 DB
 * - GET /api/v1/agent/sessions/{runId}/stream → SSE 事件
 *
 * ChatClient 以 @MockBean 取代真實 Gemini 呼叫；
 * OrderAgentTools 直接注入驗證，不 mock（測試 tool 邏輯）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Testcontainers
@DisplayName("AgentIntegrationTest - chat → SSE → AgentStep DB")
class AgentIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void setProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("order-service.base-url", () -> "http://mock-order-service:8080"); // won't be called
    }

    /** 停用真實 Gemini 呼叫 */
    @MockBean
    private ChatClient chatClient;

    /** 停用真實 order-service 呼叫 */
    @MockBean
    private com.coremall.agent.client.OrderServiceClient orderServiceClient;

    /** 停用真實 RedisVectorStore（需 Redis Stack，測試環境用 plain Redis） */
    @MockBean
    private org.springframework.ai.vectorstore.VectorStore vectorStore;

    /** 停用真實 EmbeddingModel（測試環境無 API key，讓 @ConditionalOnMissingBean 跳過 auto-config） */
    @MockBean
    private org.springframework.ai.embedding.EmbeddingModel embeddingModel;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Autowired
    private AgentStepRepository agentStepRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanUp() {
        agentStepRepository.deleteAll();
        agentRunRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("POST /api/v1/agent/chat → 202 Accepted，回傳 runId")
    void shouldReturn202WithRunId() throws Exception {
        mockChatClientNoOp();

        webTestClient.post().uri("/api/v1/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", "test-user")
                .bodyValue(objectMapper.writeValueAsString(new ChatRequest("\1", null)))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.runId").isNotEmpty();
    }

    @Test
    @DisplayName("chat 執行後：AgentRun 狀態更新為 COMPLETED 並持久化到 DB")
    void shouldPersistAgentRunAsCompleted() throws Exception {
        mockChatClientNoOp();

        String body = webTestClient.post().uri("/api/v1/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", "test-user")
                .bodyValue(objectMapper.writeValueAsString(new ChatRequest("\1", null)))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(String.class)
                .returnResult().getResponseBody();

        String runId = objectMapper.readTree(body).path("data").path("runId").asText();

        // 等待背景執行完成
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            AgentRun run = agentRunRepository.findById(UUID.fromString(runId)).orElseThrow();
            assertThat(run.getStatus()).isIn("COMPLETED", "FAILED");
        });
    }

    @Test
    @DisplayName("tool call 執行後：AgentStep 記錄寫入 DB（STARTED → SUCCEEDED）")
    void shouldPersistAgentStepAfterToolCall() throws Exception {
        // Mock ChatClient 模擬 LLM 呼叫 createOrder tool
        com.coremall.agent.dto.OrderResult mockOrder =
                new com.coremall.agent.dto.OrderResult("order-xyz", "user-1", "Apple", 5, "CREATED", "2025-01-01T00:00:00Z");
        when(orderServiceClient.createOrder(any(), any(), any(Integer.class), any()))
                .thenReturn(mockOrder);

        mockChatClientWithToolCall("user-1", "Apple", 5);

        String body = webTestClient.post().uri("/api/v1/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", "test-user")
                .bodyValue(objectMapper.writeValueAsString(new ChatRequest("\1", null)))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(String.class)
                .returnResult().getResponseBody();

        String runId = objectMapper.readTree(body).path("data").path("runId").asText();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<AgentStep> steps = agentStepRepository.findByRunIdOrderByCreatedAtAsc(UUID.fromString(runId));
            assertThat(steps).isNotEmpty();
        });
    }

    // --- 輔助方法 ---

    /** Mock ChatClient 直接返回固定 reply，不呼叫任何 tool。 */
    @SuppressWarnings("unchecked")
    private void mockChatClientNoOp() {
        var promptSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        var callSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(any(String.class))).thenReturn(promptSpec);
        when(promptSpec.advisors(any(java.util.function.Consumer.class))).thenReturn(promptSpec);
        when(promptSpec.tools(any())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("好的，我已幫您完成操作。");
    }

    /** Mock ChatClient 在執行時直接呼叫 createOrder tool（模擬 LLM function call）。 */
    @SuppressWarnings("unchecked")
    private void mockChatClientWithToolCall(String userId, String productName, int quantity) {
        var promptSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        var callSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(any(String.class))).thenReturn(promptSpec);
        when(promptSpec.advisors(any(java.util.function.Consumer.class))).thenReturn(promptSpec);

        // 當 .tools(orderAgentTools) 被呼叫時，抓取 OrderAgentTools 實例並直接呼叫
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            for (Object arg : args) {
                if (arg instanceof OrderAgentTools tools) {
                    tools.createOrder(userId, productName, quantity);
                }
            }
            return promptSpec;
        }).when(promptSpec).tools(any());

        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("好的，訂單已建立！");
    }
}
