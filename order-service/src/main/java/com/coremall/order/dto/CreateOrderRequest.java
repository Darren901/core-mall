package com.coremall.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(
        @NotBlank String userId,
        @NotBlank String productName,
        @Min(1) int quantity
) {}
