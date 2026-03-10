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
