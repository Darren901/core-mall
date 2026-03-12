## Context

order-service 目前建立訂單時完全不查詢庫存，只有在 Outbox 事件抵達 inventory-service 後才知道是否能扣減。庫存不足時走 Saga 補償取消，用戶體驗差（先成功後取消）。

## Goals / Non-Goals

**Goals:**
- 建立訂單前同步查詢庫存，不足即時回傳 400
- inventory-service 暴露 REST 查詢端點
- 保留 Saga 補償作為 race condition 最後防線

**Non-Goals:**
- 庫存鎖定（soft lock / reservation）機制
- Write-Behind 改造 inventory-service
- 庫存預留 TTL 管理

## Decisions

### 1. inventory-service 新增查詢端點

```
GET /api/inventory/{productName}
Response 200: { "productName": "iPhone 15", "quantity": 10 }
Response 404: 商品不存在
```

- 直接讀 H2（不加 Redis 快取，避免與非同步扣減的快取一致性問題）
- 回傳完整 `InventoryResponse` record

### 2. order-service 新增 InventoryClient

- 使用 WebClient（與 `OrderServiceClient` 同樣的模式，已在 agent-service 使用）
- 透過 Eureka 服務發現（`lb://inventory-service`）呼叫
- 庫存 < 請求數量 → 拋 `InsufficientStockException`（422）
- 商品不存在（404）→ 拋相同例外（保守策略：不存在也不下單）
- 呼叫失敗（網路、逾時）→ 拋 `ServiceUnavailableException`（503）

### 3. OrderCommandService.createOrder() 加入預檢

```
createOrder(request):
  1. 冪等檢查（既有）
  2. inventoryClient.checkStock(productName, quantity)  ← 新增
     └── 不足 → 拋例外，不往下走
  3. 寫 Redis（既有）
  4. 加入 pending-relay（既有）
  5. 回傳 OrderResponse（既有）
```

### 4. 例外處理

| 情況 | HTTP Status | 錯誤碼 |
|---|---|---|
| 庫存不足 | 422 | `INSUFFICIENT_STOCK` |
| 商品不存在 | 422 | `INSUFFICIENT_STOCK` |
| inventory-service 無回應 | 503 | `INVENTORY_SERVICE_UNAVAILABLE` |

> 選 422 而非 400：請求格式正確，是業務規則驗證失敗。

## Risks / Trade-offs

**TOCTOU race condition**：預檢通過後、扣減前可能被其他訂單搶走庫存。Saga 補償保留作為最後防線，此 race condition 屬極低機率（閃購場景才明顯）。

**循環依賴風險**：order-service 呼叫 inventory-service，inventory-service 透過 MQ 補償 order-service。這是單向 HTTP + 反向 MQ，不構成循環依賴。

**可用性耦合**：inventory-service 下線時 order-service 無法建立訂單。若需要 fallback（降級允許下單），可改為 circuit breaker + 樂觀策略，但目前 scope 外。
