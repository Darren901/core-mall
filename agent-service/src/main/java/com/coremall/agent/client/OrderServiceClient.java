package com.coremall.agent.client;

import com.coremall.agent.dto.OrderResult;
import com.coremall.sharedkernel.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * 封裝對 order-service 的 HTTP 呼叫，方便單元測試 mock。
 */
@Component
public class OrderServiceClient {

    private final WebClient webClient;

    public OrderServiceClient(WebClient orderServiceWebClient) {
        this.webClient = orderServiceWebClient;
    }

    public OrderResult createOrder(String userId, String productName, int quantity, String idempotencyKey) {
        return webClient.post()
                .uri("/internal/v1/orders")
                .header("X-Idempotency-Key", idempotencyKey)
                .bodyValue(Map.of("userId", userId, "productName", productName, "quantity", quantity))
                .retrieve()
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
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<OrderResult>>() {})
                .map(ApiResponse::data)
                .block();
    }

    public void cancelOrder(String orderId, String idempotencyKey) {
        webClient.delete()
                .uri("/internal/v1/orders/{orderId}", orderId)
                .header("X-Idempotency-Key", idempotencyKey)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    public OrderResult getOrder(String orderId) {
        return webClient.get()
                .uri("/internal/v1/orders/{orderId}", orderId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<OrderResult>>() {})
                .map(ApiResponse::data)
                .block();
    }
}
