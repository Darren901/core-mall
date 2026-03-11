package com.coremall.agent.client;

import com.coremall.agent.dto.OrderResult;
import com.coremall.sharedkernel.response.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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

    public OrderServiceClient(WebClient orderServiceWebClient, ObjectMapper objectMapper) {
        this.webClient = orderServiceWebClient;
        this.objectMapper = objectMapper;
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
                .block();
    }

    public void cancelOrder(String orderId, String idempotencyKey) {
        webClient.delete()
                .uri("/internal/v1/orders/{orderId}", orderId)
                .header("X-Idempotency-Key", idempotencyKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, toFriendlyError())
                .toBodilessEntity()
                .block();
    }

    public OrderResult getOrder(String orderId) {
        return webClient.get()
                .uri("/internal/v1/orders/{orderId}", orderId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, toFriendlyError())
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<OrderResult>>() {})
                .map(ApiResponse::data)
                .block();
    }

    /**
     * 將 4xx/5xx response 的 error.message 萃取為 RuntimeException。
     * 先讀 raw body String，再嘗試解析成 ApiResponse；
     * 若 response body 不符合 ApiResponse 格式（如 Spring Boot 預設錯誤），
     * 則 fallback 至泛型錯誤訊息，避免 JSON 反序列化例外洩漏到前端。
     */
    private Function<ClientResponse, Mono<? extends Throwable>> toFriendlyError() {
        return resp -> resp.bodyToMono(String.class)
                .map(body -> {
                    try {
                        ApiResponse<Void> api = objectMapper.readValue(body, new TypeReference<>() {});
                        if (api.error() != null) {
                            return new RuntimeException(api.error().message());
                        }
                    } catch (Exception ignored) {
                        // body 不是 ApiResponse 格式（例如 Spring Boot 預設 error）
                    }
                    return new RuntimeException("服務暫時無法處理請求，請稍後再試");
                });
    }
}
