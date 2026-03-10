package com.coremall.order.dto;

public record InsufficientStockPayload(
        String orderId,
        String productName,
        int requestedQty,
        int availableQty
) {}
