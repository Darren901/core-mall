package com.coremall.order.client;

import com.coremall.order.exception.InsufficientStockException;
import com.coremall.order.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private final RestClient restClient;

    public InventoryClient(RestClient inventoryRestClient) {
        this.restClient = inventoryRestClient;
    }

    /**
     * 查詢庫存是否足夠。不足或商品不存在拋 InsufficientStockException；
     * 服務不可用拋 ServiceUnavailableException。
     */
    public void checkStock(String productName, int requestedQty) {
        log.info("[InventoryClient] checkStock: productName={} qty={}", productName, requestedQty);
        try {
            InventoryResponse response = restClient.get()
                    .uri("/api/inventory/{productName}", productName)
                    .retrieve()
                    .body(InventoryResponse.class);

            if (response == null || response.quantity() < requestedQty) {
                log.warn("[InventoryClient] 庫存不足：productName={} available={} requested={}",
                        productName, response == null ? 0 : response.quantity(), requestedQty);
                throw new InsufficientStockException(productName);
            }
            log.info("[InventoryClient] 庫存足夠：productName={} available={}", productName, response.quantity());
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("[InventoryClient] 商品不存在：productName={}", productName);
            throw new InsufficientStockException(productName);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.error("[InventoryClient] inventory-service 無法連線：{}", e.getMessage());
            throw new ServiceUnavailableException("inventory-service");
        }
    }

    record InventoryResponse(String productName, int quantity) {}
}
