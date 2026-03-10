## Context

order-service 的訂單狀態存在 Redis（Write-Behind），`OrderCommandService` 透過 `StringRedisTemplate` 讀寫。`processed_events` table 已存在，供消費端冪等保護使用。`RabbitMQConfig` 目前只宣告 `order.events` exchange（發佈端）。

inventory-service 已建立 `inventory.events` exchange 與 `inventory.events.queue`，補償事件 payload 格式：
```json
{ "orderId": "uuid", "productName": "iPhone 15", "requestedQty": 2, "availableQty": 0 }
```

## Goals / Non-Goals

**Goals:**
- order-service 消費 `inventory.events.queue` 的 INSUFFICIENT 補償事件
- 以 orderId 更新 Redis 訂單狀態為 `CANCELLED`
- 冪等保護（messageId → processed_events）

**Non-Goals:**
- 不通知使用者（無 push/SSE）；Agent 下次查詢才感知狀態變更
- 不修改 Outbox/relay 流程
- 不修改 inventory-service

## Decisions

### D1：消費 inventory.events.queue（不建新 queue）

inventory-service 已建立 `inventory.events.queue` 並綁定 `inventory.events` exchange。order-service 直接消費此 queue，不另建 queue。

替代方案：order-service 自建 `order.inventory.queue` 再綁定 → 被否決，因為目前只有一個消費者，額外 queue 無意義，增加複雜度。

### D2：新增 cancelOrderBySaga(orderId) 不走 idempotency-key

現有 `cancelOrder(orderId, idempotencyKey)` 必須傳 idempotencyKey（HTTP 請求帶入）。Saga 補償不是來自 HTTP 請求，沒有 idempotency-key 語意。

新增 `cancelOrderBySaga(orderId)`：直接讀取 Redis 訂單、更新狀態為 `CANCELLED`、寫回。冪等性由 `processed_events`（messageId）在 consumer 層保護，不在 service 層重複保護。

### D3：inventory.events exchange 宣告加入 order-service RabbitMQConfig

Spring AMQP 宣告是冪等的（exchange/queue 已存在就跳過）。order-service 宣告同一個 exchange，確保無論服務啟動順序如何都能正確連線。

## Risks / Trade-offs

- **[Risk] 訂單不在 Redis（TTL 過期）**：若 Redis miss，fallback 直接更新 PostgreSQL（`orderRepository.findById → setStatus(CANCELLED) → save`）。Saga 補償是 correctness-critical 的例外流程，不適用 Write-Behind 的效能語意，直接 DB 更新反而語意清晰。若 DB 也找不到才 log warn 跳過。
- **[Trade-off] 最終一致性**：Agent 在 createOrder 後立刻回傳成功，使用者需等 Saga 補償完成（約 5~15 秒）才能透過查詢發現訂單已 CANCELLED。這是 Write-Behind + Saga 的固有 trade-off。

## Migration Plan

1. 修改 `RabbitMQConfig` 新增 inventory exchange/queue/binding bean
2. 新增 `InventoryEventConsumer`（先寫測試）
3. 新增 `OrderCommandService.cancelOrderBySaga(orderId)`（先寫測試）
4. 執行 `mvn clean verify -pl order-service`
