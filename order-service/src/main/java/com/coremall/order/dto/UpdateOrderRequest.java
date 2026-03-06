package com.coremall.order.dto;

import jakarta.validation.constraints.Min;

public record UpdateOrderRequest(
        String productName,
        @Min(1) Integer quantity
) {}
