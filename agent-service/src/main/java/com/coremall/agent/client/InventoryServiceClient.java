package com.coremall.agent.client;

import com.coremall.agent.dto.InventoryResult;
import com.coremall.sharedkernel.exception.ServiceBusinessException;
import com.coremall.sharedkernel.exception.ServiceTransientException;
import com.coremall.sharedkernel.response.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Function;

/**
 * 封裝對 inventory-service 的 HTTP 呼叫。
 * 5xx → ServiceTransientException（可重試）
 * 4xx → ServiceBusinessException（業務規則拒絕，不重試）
 */
@Component
public class InventoryServiceClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Retry retryPolicy;

    @Autowired
    public InventoryServiceClient(WebClient inventoryServiceWebClient, ObjectMapper objectMapper) {
        this(inventoryServiceWebClient, objectMapper, defaultRetryPolicy());
    }

    /** 供測試注入自訂 Retry policy。 */
    InventoryServiceClient(WebClient inventoryServiceWebClient, ObjectMapper objectMapper, Retry retryPolicy) {
        this.webClient = inventoryServiceWebClient;
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

    public InventoryResult getStock(String productName) {
        return webClient.get()
                .uri("/api/inventory/{productName}", productName)
                .retrieve()
                .onStatus(status -> status.isError(), toFriendlyError())
                .bodyToMono(InventoryResult.class)
                .retryWhen(retryPolicy)
                .block();
    }

    private Function<ClientResponse, Mono<? extends Throwable>> toFriendlyError() {
        return resp -> resp.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    if (resp.statusCode().value() == 404) {
                        return new ServiceBusinessException("查無此商品");
                    }
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
            // body 不是 ApiResponse 格式
        }
        return "服務暫時無法處理請求，請稍後再試";
    }
}
