package com.coremall.agent.client;

import com.coremall.agent.dto.OrderResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderServiceClient - HTTP 錯誤訊息萃取")
class OrderServiceClientTest {

    private MockWebServer mockWebServer;
    private OrderServiceClient client;

    private static final String ERROR_BODY_404 =
            "{\"success\":false,\"error\":{\"code\":\"ORDER_NOT_FOUND\",\"message\":\"訂單不存在\",\"details\":[]}}";
    private static final String ERROR_BODY_500 =
            "{\"success\":false,\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"系統內部錯誤\",\"details\":[]}}";

    /** Spring Boot 預設 error body（非 ApiResponse 格式）*/
    private static final String SPRING_DEFAULT_500 =
            "{\"timestamp\":\"2026-01-01T00:00:00\",\"status\":500,\"error\":\"Internal Server Error\",\"path\":\"/internal/v1/orders/xxx\"}";
    private static final String ERROR_BODY_422 =
            "{\"success\":false,\"error\":{\"code\":\"INSUFFICIENT_STOCK\",\"message\":\"庫存不足，無法建立訂單：Apple\",\"details\":[]}}";
    private static final String ERROR_BODY_503 =
            "{\"success\":false,\"error\":{\"code\":\"INVENTORY_SERVICE_UNAVAILABLE\",\"message\":\"服務暫時無法使用：inventory-service\",\"details\":[]}}";

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        client = new OrderServiceClient(webClient, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ─── createOrder ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("createOrder：422 → 例外訊息為 error.message，不含 HTTP 狀態字串")
    void shouldThrowFriendlyMessageOn422() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(422).addHeader("Content-Type", "application/json")
                .setBody(ERROR_BODY_422));

        assertThatThrownBy(() -> client.createOrder("user-1", "Apple", 5, "idem-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("庫存不足，無法建立訂單：Apple")
                .hasMessageNotContaining("422 Unprocessable Entity");
    }

    @Test
    @DisplayName("createOrder：503 → 例外訊息為 error.message，不含 HTTP 狀態字串")
    void shouldThrowFriendlyMessageOn503() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(503).addHeader("Content-Type", "application/json")
                .setBody(ERROR_BODY_503));

        assertThatThrownBy(() -> client.createOrder("user-1", "Apple", 5, "idem-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("服務暫時無法使用：inventory-service")
                .hasMessageNotContaining("503 Service Unavailable");
    }

    @Test
    @DisplayName("createOrder：200 → 正常回傳 OrderResult")
    void shouldReturnOrderResultOnSuccess() {
        mockWebServer.enqueue(successOrder("order-123"));

        OrderResult result = client.createOrder("user-1", "Apple", 5, "idem-key");

        assertThat(result.id()).isEqualTo("order-123");
    }

    // ─── updateOrder ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateOrder：404 → 例外訊息為 error.message，不含 HTTP 狀態字串")
    void shouldThrowFriendlyMessageWhenUpdateOrderNotFound() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404).addHeader("Content-Type", "application/json")
                .setBody(ERROR_BODY_404));

        assertThatThrownBy(() -> client.updateOrder("order-xxx", "Apple", 5, "idem-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("訂單不存在")
                .hasMessageNotContaining("404 Not Found");
    }

    @Test
    @DisplayName("updateOrder：500 → 例外訊息為 error.message，不含 HTTP 狀態字串")
    void shouldThrowFriendlyMessageWhenUpdateOrderFails() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500).addHeader("Content-Type", "application/json")
                .setBody(ERROR_BODY_500));

        assertThatThrownBy(() -> client.updateOrder("order-xxx", "Apple", 5, "idem-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("系統內部錯誤")
                .hasMessageNotContaining("500 Internal Server Error");
    }

    // ─── cancelOrder ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelOrder：404 → 例外訊息為 error.message，不含 HTTP 狀態字串")
    void shouldThrowFriendlyMessageWhenCancelOrderNotFound() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404).addHeader("Content-Type", "application/json")
                .setBody(ERROR_BODY_404));

        assertThatThrownBy(() -> client.cancelOrder("order-xxx", "idem-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("訂單不存在")
                .hasMessageNotContaining("404 Not Found");
    }

    @Test
    @DisplayName("cancelOrder：500 → 例外訊息為 error.message，不含 HTTP 狀態字串")
    void shouldThrowFriendlyMessageWhenCancelOrderFails() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500).addHeader("Content-Type", "application/json")
                .setBody(ERROR_BODY_500));

        assertThatThrownBy(() -> client.cancelOrder("order-xxx", "idem-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("系統內部錯誤")
                .hasMessageNotContaining("500 Internal Server Error");
    }

    // ─── getOrder ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrder：404 → 例外訊息為 error.message，不含 HTTP 狀態字串")
    void shouldThrowFriendlyMessageWhenGetOrderNotFound() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404).addHeader("Content-Type", "application/json")
                .setBody(ERROR_BODY_404));

        assertThatThrownBy(() -> client.getOrder("order-xxx"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("訂單不存在")
                .hasMessageNotContaining("404 Not Found");
    }

    @Test
    @DisplayName("getOrder：500 → 例外訊息為 error.message，不含 HTTP 狀態字串")
    void shouldThrowFriendlyMessageWhenGetOrderFails() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500).addHeader("Content-Type", "application/json")
                .setBody(ERROR_BODY_500));

        assertThatThrownBy(() -> client.getOrder("order-xxx"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("系統內部錯誤")
                .hasMessageNotContaining("500 Internal Server Error");
    }

    // ─── Spring 預設 error format fallback ────────────────────────────────────

    @Test
    @DisplayName("getOrder：500 Spring 預設 error body → 不拋 JSON 反序列化例外，回傳可讀訊息")
    void shouldHandleSpringDefaultErrorBodyWithoutDeserializationException() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500).addHeader("Content-Type", "application/json")
                .setBody(SPRING_DEFAULT_500));

        assertThatThrownBy(() -> client.getOrder("order-xxx"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageNotContaining("JSON decoding error")
                .hasMessageNotContaining("Cannot construct instance");
    }

    @Test
    @DisplayName("createOrder：500 Spring 預設 error body → 不拋 JSON 反序列化例外")
    void shouldHandleSpringDefaultErrorBodyOnCreateOrder() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500).addHeader("Content-Type", "application/json")
                .setBody(SPRING_DEFAULT_500));

        assertThatThrownBy(() -> client.createOrder("user-1", "Apple", 5, "idem-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageNotContaining("JSON decoding error")
                .hasMessageNotContaining("Cannot construct instance");
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private MockResponse successOrder(String orderId) {
        return new MockResponse()
                .setResponseCode(200).addHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"data\":{\"id\":\"" + orderId + "\",\"userId\":\"user-1\"," +
                        "\"productName\":\"Apple\",\"quantity\":5,\"status\":\"CREATED\"," +
                        "\"createdAt\":\"2025-01-01T00:00:00Z\"}}");
    }
}
