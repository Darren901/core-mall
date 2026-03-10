package com.coremall.inventory.dto;

public record OrderEventPayload(
        String id,
        String userId,
        String productName,
        int quantity,
        String status,
        String createdAt
) {}
