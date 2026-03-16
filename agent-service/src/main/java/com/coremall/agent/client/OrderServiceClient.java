package com.coremall.agent.client;

import com.coremall.agent.dto.OrderResult;
import com.coremall.sharedkernel.exception.ServiceBusinessException;
import com.coremall.sharedkernel.exception.ServiceTransientException;
import com.coremall.sharedkernel.response.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;


/**
 * 封裝對 order-service 的 HTTP 呼叫，方便單元測試 mock。
 * 所有方法均透過 onStatus() 將 error body 中的 error.message 轉換為例外訊息，
 * 避免 WebClientResponseException 洩漏原始 HTTP 狀態字串與 URL。
 */
@Component
public class OrderServiceClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Retry retryPolicy;

    @Autowired
    public OrderServiceClient(WebClient orderServiceWebClient, ObjectMapper objectMapper) {
        this(orderServiceWebClient, objectMapper, defaultRetryPolicy());
    }

    /** 供測試注入自訂 Retry policy，避免 backoff 造成測試緩慢或 MockWebServer 耗盡響應。 */
    OrderServiceClient(WebClient orderServiceWebClient, ObjectMapper objectMapper, Retry retryPolicy) {
        this.webClient = orderServiceWebClient;
        this.objectMapper = objectMapper;
        this.retryPolicy = retryPolicy;
    }

    private static Retry defaultRetryPolicy() {
        return Retry.backoff(3, Duration.ofMillis(200))
                .maxBackoff(Duration.ofSeconds(2))
                .jitter(0.5)
                .filter(e -> e instanceof ServiceTransientException || e instanceof IOException)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    public OrderResult createOrder(String userId, String productName, int quantity, String idempotencyKey) {
        return webClient.post()
                .uri("/internal/v1/orders")
                .header("X-Idempotency-Key", idempotencyKey)
                .bodyValue(Map.of("userId", userId, "productName", productName, "quantity", quantity))
                .retrieve()
                .onStatus(HttpStatusCode::isError, toFriendlyError())
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<OrderResult>>() {})
                .map(ApiResponse::data)
                .retryWhen(retrySpec())
                .block();
    }

    public OrderResult updateOrder(String orderId, String productName, Integer quantity, String idempotencyKey) {
        return webClient.patch()
                .uri("/internal/v1/orders/{orderId}", orderId)
                .header("X-Idempotency-Key", idempotencyKey)
                .bodyValue(Map.of("productName", productName != null ? productName : "",
                        "quantity", quantity != null ? quantity : 0))
                .retrieve()
                .onStatus(HttpStatusCode::isError, toFriendlyError())
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<OrderResult>>() {})
                .map(ApiResponse::data)
                .retryWhen(retrySpec())
                .block();
    }

    public void cancelOrder(String orderId, String idempotencyKey) {
        webClient.delete()
                .uri("/internal/v1/orders/{orderId}", orderId)
                .header("X-Idempotency-Key", idempotencyKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, toFriendlyError())
                .toBodilessEntity()
                .retryWhen(retrySpec())
                .block();
    }

    public OrderResult getOrder(String orderId) {
        return webClient.get()
                .uri("/internal/v1/orders/{orderId}", orderId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, toFriendlyError())
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<OrderResult>>() {})
                .map(ApiResponse::data)
                .retryWhen(retrySpec())
                .block();
    }

    /**
     * 將 4xx/5xx response 的 error.message 萃取為對應例外：
     * - 5xx → ServiceTransientException（可重試）
     * - 4xx → ServiceBusinessException（業務規則拒絕，不重試）
     */
    private Function<ClientResponse, Mono<? extends Throwable>> toFriendlyError() {
        return resp -> resp.bodyToMono(String.class)
                .map(body -> {
                    String message = extractMessage(body);
                    if (resp.statusCode().is5xxServerError()) {
                        return new ServiceTransientException(message);
                    }
                    return new ServiceBusinessException(message);
                });
    }

    private String extractMessage(String body) {
        try {
            ApiResponse<Void> api = objectMapper.readValue(body, new TypeReference<>() {});
            if (api.error() != null) {
                return api.error().message();
            }
        } catch (Exception ignored) {
            // body 不是 ApiResponse 格式（例如 Spring Boot 預設 error）
        }
        return "服務暫時無法處理請求，請稍後再試";
    }

    /**
     * 暫時性錯誤（ServiceTransientException / IOException）最多重試 3 次，exponential backoff。
     * RetryExhaustedException 展開為原始的 ServiceTransientException，統一拋出語意。
     */
    private Retry retrySpec() {
        return retryPolicy;
    }
}
