package com.coremall.order.controller;

import com.coremall.order.dto.CreateOrderRequest;
import com.coremall.order.dto.OrderResponse;
import com.coremall.order.dto.UpdateOrderRequest;
import com.coremall.order.exception.LockConflictException;
import com.coremall.order.exception.OrderNotFoundException;
import com.coremall.order.service.OrderCommandService;
import com.coremall.order.service.OrderQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@DisplayName("OrderController - /internal/v1/orders")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderCommandService commandService;

    @MockBean
    private OrderQueryService queryService;

    @Test
    @DisplayName("POST / 建立訂單成功回傳 201")
    void shouldReturn201WhenCreateSucceeds() throws Exception {
        String orderId = UUID.randomUUID().toString();
        OrderResponse resp = new OrderResponse(orderId, "u1", "Apple", 3, "CREATED", "2026-03-06T12:00:00");
        when(commandService.createOrder(any(), any())).thenReturn(resp);

        mockMvc.perform(post("/internal/v1/orders")
                        .header("X-Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest("u1", "Apple", 3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    @Test
    @DisplayName("POST / 鎖衝突回傳 409")
    void shouldReturn409WhenLockConflict() throws Exception {
        when(commandService.createOrder(any(), any())).thenThrow(new LockConflictException("u1"));

        mockMvc.perform(post("/internal/v1/orders")
                        .header("X-Idempotency-Key", "idem-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest("u1", "Apple", 3))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ORDER_LOCK_CONFLICT"));
    }

    @Test
    @DisplayName("POST / 參數驗證失敗回傳 400")
    void shouldReturn400WhenValidationFails() throws Exception {
        mockMvc.perform(post("/internal/v1/orders")
                        .header("X-Idempotency-Key", "idem-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest("u1", "Apple", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /{orderId} 查詢成功回傳 200")
    void shouldReturn200WhenGetOrder() throws Exception {
        String orderId = UUID.randomUUID().toString();
        when(queryService.getOrder(orderId))
                .thenReturn(new OrderResponse(orderId, "u1", "Apple", 3, "CREATED", "2026-03-06T12:00:00"));

        mockMvc.perform(get("/internal/v1/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(orderId));
    }

    @Test
    @DisplayName("GET /{orderId} 訂單不存在回傳 404")
    void shouldReturn404WhenOrderNotFound() throws Exception {
        String orderId = UUID.randomUUID().toString();
        when(queryService.getOrder(orderId)).thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(get("/internal/v1/orders/" + orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /{orderId} 更新成功回傳 200")
    void shouldReturn200WhenUpdateSucceeds() throws Exception {
        String orderId = UUID.randomUUID().toString();
        OrderResponse resp = new OrderResponse(orderId, "u1", "Banana", 5, "UPDATED", "2026-03-06T12:00:00");
        when(commandService.updateOrder(eq(orderId), any(), any())).thenReturn(resp);

        mockMvc.perform(patch("/internal/v1/orders/" + orderId)
                        .header("X-Idempotency-Key", "update-idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateOrderRequest("Banana", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UPDATED"));
    }

    @Test
    @DisplayName("DELETE /{orderId} 取消成功回傳 204")
    void shouldReturn204WhenCancelSucceeds() throws Exception {
        String orderId = UUID.randomUUID().toString();
        doNothing().when(commandService).cancelOrder(eq(orderId), any());

        mockMvc.perform(delete("/internal/v1/orders/" + orderId)
                        .header("X-Idempotency-Key", "cancel-idem-1"))
                .andExpect(status().isNoContent());
    }
}
