package com.coremall.order.dto;

public record OrderResponse(
        String id,
        String userId,
        String productName,
        int quantity,
        String status,
        String createdAt
) {}
