package com.coremall.agent.client;

import com.coremall.agent.dto.InventoryResult;
import com.coremall.sharedkernel.exception.ServiceBusinessException;
import com.coremall.sharedkernel.exception.ServiceTransientException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InventoryServiceClient - HTTP 呼叫與錯誤處理")
class InventoryServiceClientTest {

    private MockWebServer mockWebServer;
    private InventoryServiceClient client;

    private static final String SUCCESS_BODY =
            "{\"productName\":\"iPhone 15\",\"quantity\":10}";
    private static final String ERROR_BODY_404 =
            "{\"success\":false,\"error\":{\"code\":\"INVENTORY_NOT_FOUND\",\"message\":\"商品不存在\",\"details\":[]}}";
    private static final String ERROR_BODY_500 =
            "{\"success\":false,\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"系統內部錯誤\",\"details\":[]}}";
    private static final String SPRING_DEFAULT_500 =
            "{\"timestamp\":\"2026-01-01T00:00:00\",\"status\":500,\"error\":\"Internal Server Error\",\"path\":\"/api/inventory/iPhone 15\"}";

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        client = buildClient(noRetryPolicy());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("200 → 正常回傳 InventoryResult")
    void shouldReturnInventoryResultOnSuccess() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200).addHeader("Content-Type", "application/json")
                .setBody(SUCCESS_BODY));

        InventoryResult result = client.getStock("iPhone 15");

        assertThat(result.productName()).isEqualTo("iPhone 15");
        assertThat(result.quantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("404 有 body → 拋出 ServiceBusinessException，訊息固定為「查無此商品」")
    void shouldThrowBusinessExceptionOn404WithBody() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404).addHeader("Content-Type", "application/json")
                .setBody(ERROR_BODY_404));

        assertThatThrownBy(() -> client.getStock("不存在商品"))
                .isInstanceOf(ServiceBusinessException.class)
                .hasMessage("查無此商品");
    }

    @Test
    @DisplayName("404 空 body（inventory-service notFound()）→ 拋出 ServiceBusinessException，訊息為「查無此商品」")
    void shouldThrowBusinessExceptionOn404WithEmptyBody() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        assertThatThrownBy(() -> client.getStock("不存在商品"))
                .isInstanceOf(ServiceBusinessException.class)
                .hasMessage("查無此商品");
    }

    @Test
    @DisplayName("500 → 拋出 ServiceTransientException，訊息來自 error.message")
    void shouldThrowTransientExceptionOn500() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500).addHeader("Content-Type", "application/json")
                .setBody(ERROR_BODY_500));

        assertThatThrownBy(() -> client.getStock("iPhone 15"))
                .isInstanceOf(ServiceTransientException.class)
                .hasMessage("系統內部錯誤")
                .hasMessageNotContaining("500 Internal Server Error");
    }

    @Test
    @DisplayName("500 Spring 預設 error body → 不拋 JSON 反序列化例外，回傳 fallback 訊息")
    void shouldHandleSpringDefaultErrorBodyWithoutDeserializationException() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500).addHeader("Content-Type", "application/json")
                .setBody(SPRING_DEFAULT_500));

        assertThatThrownBy(() -> client.getStock("iPhone 15"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageNotContaining("JSON decoding error")
                .hasMessageNotContaining("Cannot construct instance");
    }

    @Nested
    @DisplayName("Retry 行為")
    class RetryTest {

        private InventoryServiceClient retryClient;

        @BeforeEach
        void setUpRetryClient() {
            retryClient = buildClient(fastRetryPolicy());
        }

        @Test
        @DisplayName("5xx 第一次失敗，第二次成功 → 回傳正確結果")
        void shouldRetryAndSucceedOnSecondAttempt() {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(500).addHeader("Content-Type", "application/json")
                    .setBody(ERROR_BODY_500));
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200).addHeader("Content-Type", "application/json")
                    .setBody(SUCCESS_BODY));

            InventoryResult result = retryClient.getStock("iPhone 15");

            assertThat(result.productName()).isEqualTo("iPhone 15");
            assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("5xx 連續 4 次失敗（首次 + 3 retry）→ 拋 ServiceTransientException")
        void shouldExhaustRetryAndThrowTransientException() {
            for (int i = 0; i < 4; i++) {
                mockWebServer.enqueue(new MockResponse()
                        .setResponseCode(500).addHeader("Content-Type", "application/json")
                        .setBody(ERROR_BODY_500));
            }

            assertThatThrownBy(() -> retryClient.getStock("iPhone 15"))
                    .isInstanceOf(ServiceTransientException.class);
            assertThat(mockWebServer.getRequestCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("4xx 不觸發 retry → 只發出 1 個請求")
        void shouldNotRetryOn4xx() {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(404).addHeader("Content-Type", "application/json")
                    .setBody(ERROR_BODY_404));

            assertThatThrownBy(() -> retryClient.getStock("不存在"))
                    .isInstanceOf(ServiceBusinessException.class);
            assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private InventoryServiceClient buildClient(Retry retryPolicy) {
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(Duration.ofSeconds(3))))
                .build();
        return new InventoryServiceClient(webClient, new ObjectMapper(), retryPolicy);
    }

    private static Retry noRetryPolicy() {
        return Retry.fixedDelay(3, Duration.ZERO).filter(e -> false);
    }

    private static Retry fastRetryPolicy() {
        return Retry.fixedDelay(3, Duration.ZERO)
                .filter(e -> e instanceof ServiceTransientException || e instanceof IOException)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }
}
