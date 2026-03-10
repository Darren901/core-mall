## Why

inventory-service 在庫存不足時發布補償事件至 `inventory.events` exchange，但目前沒有消費者處理此事件，導致 Saga 補償流程不完整。訂單狀態停留在 `CREATED`，即使實際上庫存已不足、商品無法出貨，形成資料不一致。新增 order-service 消費補償事件，將訂單自動改為 `CANCELLED`，讓整個 Saga 閉環。

## What Changes

- order-service 新增 `InventoryEventConsumer`，監聽 `inventory.events.queue`，消費 `inventory.INSUFFICIENT` 補償事件
- 收到補償事件後，以 orderId 找到 Redis 中的訂單，將狀態改為 `CANCELLED`
- 冪等保護：以 RabbitMQ messageId 查 `processed_events` table，防止重複取消
- order-service 的 `RabbitMQConfig` 新增 `inventory.events` exchange 宣告與 binding

## Capabilities

### New Capabilities

（無新 capability，屬於 order-lifecycle 的延伸）

### Modified Capabilities

- `order-lifecycle`：新增「收到庫存補償事件後自動取消訂單」的行為

## Impact

- **order-service/config/RabbitMQConfig.java**：新增 `inventory.events` TopicExchange bean、`inventory.events.queue` Queue bean、Binding
- **order-service/service/InventoryEventConsumer.java**：新增 @RabbitListener consumer
- **order-service/service/OrderCommandService.java**：可能需要新增 `cancelOrderBySaga(orderId)` 方法（不走 idempotency-key，直接更新 Redis 狀態）
- **不影響** agent-service、inventory-service、gateway-service
