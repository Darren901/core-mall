package com.coremall.order.controller;

import com.coremall.order.dto.CreateOrderRequest;
import com.coremall.order.dto.OrderResponse;
import com.coremall.order.dto.UpdateOrderRequest;
import com.coremall.order.service.OrderCommandService;
import com.coremall.order.service.OrderQueryService;
import com.coremall.sharedkernel.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/orders")
public class OrderController {

    private final OrderCommandService commandService;
    private final OrderQueryService queryService;

    public OrderController(OrderCommandService commandService, OrderQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(commandService.createOrder(request, idempotencyKey));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> get(@PathVariable String orderId) {
        return ApiResponse.success(queryService.getOrder(orderId));
    }

    @PatchMapping("/{orderId}")
    public ApiResponse<OrderResponse> update(
            @PathVariable String orderId,
            @Valid @RequestBody UpdateOrderRequest request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(commandService.updateOrder(orderId, request, idempotencyKey));
    }

    @DeleteMapping("/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @PathVariable String orderId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        commandService.cancelOrder(orderId, idempotencyKey);
    }
}
