## ADDED Requirements

### Requirement: 消費 ORDER_CREATED 事件並扣減庫存
系統 SHALL 監聽 `inventory.order.queue`，收到 ORDER_CREATED 事件後依 `productName` 與 `quantity` 扣減對應庫存。

#### Scenario: 庫存足夠，扣減成功
- **WHEN** 收到 ORDER_CREATED 事件，productName="iPhone 15"，quantity=2，庫存現有 10
- **THEN** 系統 SHALL 將該商品庫存更新為 8，並儲存至 DB

#### Scenario: 商品不存在，跳過不補償
- **WHEN** 收到 ORDER_CREATED 事件，productName="不存在的商品"
- **THEN** 系統 SHALL 記錄 WARN log 並跳過，不發補償事件、不拋例外

---

### Requirement: 庫存不足時發布補償事件
系統 SHALL 在庫存數量小於請求數量時，發布 INSUFFICIENT 補償事件至 `inventory.events` exchange（routing key `inventory.INSUFFICIENT`）。

#### Scenario: 庫存不足，發補償事件
- **WHEN** 收到 ORDER_CREATED 事件，productName="MacBook Pro"，quantity=10，庫存現有 3
- **THEN** 系統 SHALL 發布補償事件，payload 包含 orderId、productName、requestedQty=10、availableQty=3
- **AND** 庫存數量 SHALL 保持不變（不扣減）

---

### Requirement: 冪等消費保護
系統 SHALL 以 RabbitMQ `MessageProperties.messageId` 作為冪等 key，同一 messageId 的訊息只處理一次。

#### Scenario: 重複訊息，冪等跳過
- **WHEN** 同一 messageId 的 ORDER_CREATED 事件被投遞兩次
- **THEN** 第二次投遞 SHALL 被跳過，庫存只扣減一次
- **AND** 不發重複補償事件

#### Scenario: 不同 messageId，正常處理
- **WHEN** 兩筆不同 messageId 的訂單事件依序投遞
- **THEN** 兩筆 SHALL 各自獨立處理，庫存各自扣減

---

### Requirement: 補償事件可被下游消費
系統 SHALL 將補償事件發佈至 `inventory.events.queue`，確保下游消費者可接收。

#### Scenario: 補償事件出現在 queue
- **WHEN** 庫存不足觸發補償事件
- **THEN** `inventory.events.queue` SHALL 收到包含正確 payload 的訊息

---

### Requirement: 消費 ORDER_CANCELLED 事件並返還庫存
系統 SHALL 監聽 `inventory.order.queue`，收到 `ORDER_CANCELLED` 事件（使用者主動取消）後，將對應商品庫存返還。

**注意**：僅訂閱 routing key `order.ORDER_CANCELLED`，不訂閱 `order.ORDER_SAGA_CANCELLED`，因 Saga 補償取消時庫存從未被扣過。

#### Scenario: 收到 ORDER_CANCELLED 事件，庫存返還成功
- **WHEN** 收到 ORDER_CANCELLED 事件，productName="iPhone 15"，quantity=2，庫存現有 8
- **THEN** 系統 SHALL 將該商品庫存更新為 10，並儲存至 DB

#### Scenario: 收到 ORDER_CANCELLED 事件，商品不存在
- **WHEN** 收到 ORDER_CANCELLED 事件，productName="不存在的商品"
- **THEN** 系統 SHALL 記錄 WARN log 並跳過，不拋例外

#### Scenario: 重複 ORDER_CANCELLED 事件，冪等跳過
- **WHEN** 相同 messageId 的 ORDER_CANCELLED 事件被投遞兩次
- **THEN** 第二次 SHALL 被冪等跳過，庫存只返還一次

---

### Requirement: 庫存查詢不影響扣減邏輯
庫存查詢端點為唯讀操作，不鎖定庫存。非同步 MQ 扣減流程與 Saga 補償保持不變，作為 TOCTOU race condition 的最後防線。

#### Scenario: 查詢後下單，庫存仍由 MQ 扣減
- **WHEN** 預檢通過後建立訂單，Outbox 事件觸發 inventory-service 扣減
- **THEN** 扣減邏輯與先前相同，不受查詢影響
