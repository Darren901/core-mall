package com.coremall.inventory.dto;

public record InsufficientStockEvent(
        String orderId,
        String productName,
        int requestedQty,
        int availableQty
) {}
