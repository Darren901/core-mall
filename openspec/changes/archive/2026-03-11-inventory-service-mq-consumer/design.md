## Context

order-service 透過 Outbox Pattern 將領域事件寫入 DB，再由 RabbitMQ 發佈至 `order.events` exchange（routing key `order.ORDER_CREATED`）。目前下游沒有真實消費者，Outbox 閉環尚未形成。本次新增 inventory-service，作為第一個真實下游消費者。

H2 in-memory DB 用於開發練習（focus 在 MQ 消費邏輯），不是生產設計。

## Goals / Non-Goals

**Goals:**
- 新增獨立的 inventory-service（port 8084），消費 `order.ORDER_CREATED` 事件
- 實作冪等性保護（`processed_events` table），防止重複消費
- 庫存不足時發布補償事件至 `inventory.events` exchange
- 100% TDD：每個功能先寫測試，再寫實作

**Non-Goals:**
- 不實作 HTTP API（無 Controller）——庫存查詢不在本次範圍
- 不使用 PostgreSQL——H2 in-memory 已足夠練習目的
- 不實作 Dead Letter Queue (DLQ) 或 retry 機制
- 不修改任何既有服務

## Decisions

### D1：Queue 與 Exchange 設計

- 消費端：新建 `inventory.order.queue`，綁定既有 `order.events` exchange（routing key `order.ORDER_CREATED`）
- 發佈端：新建 `inventory.events` Topic Exchange + `inventory.events.queue`（routing key `inventory.#`）
- 不與 order-service 共用 queue，確保服務邊界獨立

替代方案：共用 queue——被否決，因為 queue 共用會導致競爭消費，破壞各服務獨立消費的語意。

### D2：冪等性以 messageId 為 key

使用 RabbitMQ 的 `MessageProperties.messageId` 作為冪等 key，存入 `processed_events` table。
與 order-service 的 Outbox `ProcessedEvent` pattern 一致。

替代方案：以 orderId 去重——被否決，因為 orderId 只對 ORDER_CREATED 有語意，未來擴充其他事件類型會衝突；messageId 是訊息層級的唯一識別，更通用。

### D3：商品不存在的處理

若 `Inventory.findById(productName)` 查無結果，記錄 WARN log 後直接跳過，**不發補償事件、不拋例外**。
理由：補償事件的語意是「庫存不足」，商品不存在是資料問題，應由監控告警處理，不應觸發業務補償流程。

### D4：使用 productName 作為庫存主鍵

`Inventory` entity 以 `productName` 為 PK，簡化練習複雜度，不引入 productId 等概念。

## Risks / Trade-offs

- **[Risk] H2 重啟清空資料** → 每次啟動由 `DataInitializer` 重新寫入種子資料，開發環境可接受
- **[Risk] messageId 由 order-service 負責填入** → 若 order-service 未設定 messageId，冪等保護失效；需確認 order-service 的 Outbox 發佈程式碼有設定 `messageId`
- **[Trade-off] 沒有 DLQ** → 消費失敗的訊息會觸發 RabbitMQ 預設 retry，極端情況可能造成 poison message；練習目的下接受此限制

## Migration Plan

1. 根目錄 `pom.xml` 新增 `<module>inventory-service</module>`
2. 建立 inventory-service 完整模組（TDD 順序：測試 → 實作）
3. 啟動順序：docker-compose（基礎設施）→ discovery → order → inventory
4. Rollback：移除 module entry，其他服務完全不受影響

## Open Questions

- order-service 的 Outbox publisher 是否有正確設定 `MessageProperties.messageId`？需在整合測試中驗證。
