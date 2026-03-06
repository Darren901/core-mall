package com.coremall.agent.dto;

/** order-service 回傳的訂單資料（agent-service 本地 DTO，不依賴 order-service 模組）。 */
public record OrderResult(
        String id,
        String userId,
        String productName,
        int quantity,
        String status,
        String createdAt
) {}
