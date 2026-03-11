package com.coremall.agent.client;

import com.coremall.agent.dto.OrderResult;
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

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        client = new OrderServiceClient(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("createOrder：422 回應 → 例外訊息應為 error.message，不含原始 HTTP 狀態字串")
    void shouldThrowFriendlyMessageOn422() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(422)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"success\":false,\"error\":{\"code\":\"INSUFFICIENT_STOCK\"," +
                        "\"message\":\"庫存不足，無法建立訂單：Apple\",\"details\":[]}}"));

        assertThatThrownBy(() -> client.createOrder("user-1", "Apple", 5, "idem-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("庫存不足，無法建立訂單：Apple")
                .hasMessageNotContaining("422 Unprocessable Entity");
    }

    @Test
    @DisplayName("createOrder：503 回應 → 例外訊息應為 error.message，不含原始 HTTP 狀態字串")
    void shouldThrowFriendlyMessageOn503() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(503)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"success\":false,\"error\":{\"code\":\"INVENTORY_SERVICE_UNAVAILABLE\"," +
                        "\"message\":\"服務暫時無法使用：inventory-service\",\"details\":[]}}"));

        assertThatThrownBy(() -> client.createOrder("user-1", "Apple", 5, "idem-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("服務暫時無法使用：inventory-service")
                .hasMessageNotContaining("503 Service Unavailable");
    }

    @Test
    @DisplayName("createOrder：200 → 正常回傳 OrderResult")
    void shouldReturnOrderResultOnSuccess() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"data\":{\"id\":\"order-123\",\"userId\":\"user-1\"," +
                        "\"productName\":\"Apple\",\"quantity\":5,\"status\":\"CREATED\"," +
                        "\"createdAt\":\"2025-01-01T00:00:00Z\"}}"));

        OrderResult result = client.createOrder("user-1", "Apple", 5, "idem-key");

        assertThat(result.id()).isEqualTo("order-123");
    }
}
