## ADDED Requirements

### Requirement: 內部訂單操作
系統必須僅透過 Gateway 無法路由的內部 API endpoint 執行訂單的建立、更新、取消與查詢操作。

#### Scenario: 透過內部 endpoint 建立訂單
- **WHEN** agent-service 以有效 payload 呼叫 `POST /internal/v1/orders`
- **THEN** 系統應立即寫入 Redis 並回傳 HTTP 201 及訂單 ID

#### Scenario: 透過內部 endpoint 更新訂單
- **WHEN** agent-service 以有效 payload 呼叫 `PATCH /internal/v1/orders/{id}`
- **THEN** 系統應更新 Redis 並回傳 HTTP 200 及更新後的訂單

#### Scenario: 透過內部 endpoint 取消訂單
- **WHEN** agent-service 呼叫 `DELETE /internal/v1/orders/{id}`
- **THEN** 系統應將 Redis 中的訂單狀態標記為 CANCELLED 並回傳 HTTP 204

#### Scenario: 透過內部 endpoint 查詢訂單狀態
- **WHEN** agent-service 呼叫 `GET /internal/v1/orders/{id}`
- **THEN** 系統應優先從 Redis 回傳訂單狀態，若 cache miss 則 fallback 至 PostgreSQL

#### Scenario: 直接公開存取被拒絕
- **WHEN** 任何客戶端嘗試透過 Gateway 直接呼叫 `/internal/v1/orders/**`
- **THEN** Gateway 應無此路徑的路由，回傳 404

---

### Requirement: Redis Write-Behind 持久化
系統必須優先將訂單狀態寫入 Redis，並非同步持久化至 PostgreSQL。

#### Scenario: Write-Behind relay 成功執行
- **WHEN** Redis 中存在尚未持久化至 PostgreSQL 的訂單資料
- **THEN** 排程 relay 應在同一交易中將訂單寫入 PostgreSQL 並建立 outbox_event

#### Scenario: Cache miss fallback 至 PostgreSQL
- **WHEN** Redis 中找不到訂單資料
- **THEN** 系統應查詢 PostgreSQL 並重新填入 Redis 快取

---

### Requirement: 分散式鎖防止並發訂單變更
系統必須使用分散式鎖防止對同一訂單的並發變更。

#### Scenario: 成功取得鎖
- **WHEN** agent-service 在無並發請求的情況下要求更新或取消訂單
- **THEN** 系統應取得 Redis 鎖、執行變更並釋放鎖

#### Scenario: 並發請求被阻擋
- **WHEN** 兩個並發請求同時嘗試對同一訂單進行變更
- **THEN** 僅一個請求應成功；另一個應收到 transient error 並可重試

---

### Requirement: 訂單操作冪等性
系統必須保證使用相同冪等鍵的重試操作不會產生重複的副作用。

#### Scenario: 重複的建立請求
- **WHEN** agent-service 以已存在於 Redis 的冪等鍵呼叫 create-order
- **THEN** 系統應回傳已存在的訂單，不建立新訂單

#### Scenario: 重複的更新請求
- **WHEN** agent-service 以已存在的冪等鍵呼叫 update-order
- **THEN** 系統應回傳先前記錄的結果，不重新套用更新

---

### Requirement: Outbox Pattern 確保最終一致性
系統必須透過 Outbox Pattern 將訂單事件發布至 RabbitMQ，以保證訊息可靠投遞。

#### Scenario: Outbox event 與訂單持久化同交易建立
- **WHEN** relay 將訂單寫入 PostgreSQL
- **THEN** 應在同一資料庫交易中建立 outbox_event

#### Scenario: Outbox relay 發布至 RabbitMQ
- **WHEN** 存在未發布的 outbox_event
- **THEN** relay 應將其發布至 RabbitMQ，並僅在確認後標記為已發布

#### Scenario: 重複訊息被冪等消費
- **WHEN** 消費端收到相同的訂單事件超過一次
- **THEN** 應透過檢查已處理事件 ID 記錄，確保只處理一次
