## ADDED Requirements

### Requirement: 收到庫存不足補償事件後自動取消訂單
系統 SHALL 監聽 `inventory.events.queue`，收到 `inventory.INSUFFICIENT` 補償事件後，依 orderId 將對應訂單狀態更新為 `CANCELLED`。

#### Scenario: 收到 INSUFFICIENT 事件，訂單存在於 Redis
- **WHEN** inventory-service 發布 INSUFFICIENT 補償事件，orderId 對應訂單存在 Redis 且狀態為 CREATED
- **THEN** 系統 SHALL 將該訂單狀態改為 `CANCELLED` 並寫回 Redis

#### Scenario: 收到 INSUFFICIENT 事件，訂單不在 Redis 但在 DB（TTL 過期）
- **WHEN** inventory-service 發布 INSUFFICIENT 補償事件，orderId 對應訂單不在 Redis 但存在於 PostgreSQL
- **THEN** 系統 SHALL 直接更新 PostgreSQL 訂單狀態為 CANCELLED

#### Scenario: 收到 INSUFFICIENT 事件，Redis 與 DB 皆無此訂單
- **WHEN** inventory-service 發布 INSUFFICIENT 補償事件，orderId 在 Redis 與 PostgreSQL 均不存在
- **THEN** 系統 SHALL 記錄 WARN log 並跳過，不拋例外

#### Scenario: 重複補償事件，冪等跳過
- **WHEN** 相同 messageId 的 INSUFFICIENT 事件被投遞兩次
- **THEN** 第二次 SHALL 被冪等跳過，訂單狀態不重複變更
