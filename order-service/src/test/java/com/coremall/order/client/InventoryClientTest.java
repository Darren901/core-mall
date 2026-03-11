package com.coremall.order.client;

import com.coremall.order.exception.InsufficientStockException;
import com.coremall.order.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("InventoryClient - 庫存查詢 HTTP 呼叫")
class InventoryClientTest {

    private MockRestServiceServer mockServer;
    private InventoryClient inventoryClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://inventory-service");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        inventoryClient = new InventoryClient(builder.build());
    }

    @Test
    @DisplayName("庫存足夠時，不拋例外")
    void shouldPassWhenStockSufficient() {
        mockServer.expect(requestTo("http://inventory-service/api/inventory/iPhone%2015"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"productName\":\"iPhone 15\",\"quantity\":10}",
                        MediaType.APPLICATION_JSON));

        assertThatCode(() -> inventoryClient.checkStock("iPhone 15", 5))
                .doesNotThrowAnyException();

        mockServer.verify();
    }

    @Test
    @DisplayName("庫存不足時，拋出 InsufficientStockException")
    void shouldThrowWhenStockInsufficient() {
        mockServer.expect(requestTo("http://inventory-service/api/inventory/MacBook%20Pro"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"productName\":\"MacBook Pro\",\"quantity\":3}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> inventoryClient.checkStock("MacBook Pro", 10))
                .isInstanceOf(InsufficientStockException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("商品不存在（404）時，拋出 InsufficientStockException")
    void shouldThrowWhenProductNotFound() {
        mockServer.expect(requestTo("http://inventory-service/api/inventory/%E4%B8%8D%E5%AD%98%E5%9C%A8%E5%95%86%E5%93%81"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> inventoryClient.checkStock("不存在商品", 1))
                .isInstanceOf(InsufficientStockException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("inventory-service 回傳 5xx 時，拋出 ServiceUnavailableException")
    void shouldThrowWhenServiceError() {
        mockServer.expect(requestTo("http://inventory-service/api/inventory/iPhone%2015"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> inventoryClient.checkStock("iPhone 15", 1))
                .isInstanceOf(ServiceUnavailableException.class);

        mockServer.verify();
    }
}
