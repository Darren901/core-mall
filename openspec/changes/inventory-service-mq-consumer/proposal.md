## Why

專案的 Outbox Pattern 目前只有「發佈端」，order-service 的 `OrderEventConsumer` 僅為佔位實作，Outbox 閉環尚未完成。新增 inventory-service 作為真正的下游消費者，接收訂單事件 → 扣減庫存 → 庫存不足時發補償事件，讓整個事件驅動架構真正跑通。

## What Changes

- 新增 `inventory-service` Maven 模組（port 8084），獨立部署
- 監聽既有 `order.events` exchange 的 `order.ORDER_CREATED` routing key
- 實作庫存扣減邏輯：庫存足夠扣減並記錄；庫存不足發補償事件至 `inventory.events` exchange
- 冪等保護：以 `processed_events` table 記錄已處理的 messageId，防止重複消費
- 根目錄 `pom.xml` 新增 `<module>inventory-service</module>`

## Capabilities

### New Capabilities

- `inventory-stock-deduction`：消費 ORDER_CREATED 事件，扣減對應商品庫存，庫存不足時發布 INSUFFICIENT 補償事件

### Modified Capabilities

（無）

## Impact

- **根目錄 pom.xml**：新增 inventory-service module
- **新模組 inventory-service**：全新 Spring Boot 服務，依賴 RabbitMQ（共用）、H2 in-memory DB
- **不影響**現有 order-service、agent-service、gateway-service 任何程式碼
- **RabbitMQ**：新增 `inventory.order.queue`（綁定既有 `order.events`）與 `inventory.events` exchange + `inventory.events.queue`（新建）
