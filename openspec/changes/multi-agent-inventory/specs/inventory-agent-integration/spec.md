## ADDED Requirements

### Requirement: InventoryServiceClient HTTP 呼叫
agent-service SHALL 透過 `InventoryServiceClient` 呼叫 inventory-service `GET /api/inventory/{productName}`，並以 `toFriendlyError()` 轉換錯誤回應。

#### Scenario: 查詢有庫存的商品
- **WHEN** InventoryServiceClient 呼叫 `GET /api/inventory/iPhone 15`，inventory-service 回傳 200 及 `{"productName":"iPhone 15","quantity":10}`
- **THEN** 回傳 `InventoryResult { productName="iPhone 15", quantity=10 }`

#### Scenario: 查詢不存在的商品（404）
- **WHEN** InventoryServiceClient 呼叫 `GET /api/inventory/不存在商品`，inventory-service 回傳 404
- **THEN** 拋出 `ServiceBusinessException`，訊息來自 response body 的 error.message（或 fallback）

#### Scenario: inventory-service 暫時不可用（5xx）
- **WHEN** inventory-service 回傳 500
- **THEN** 拋出 `ServiceTransientException`，並依 retry policy 重試最多 3 次

---

### Requirement: InventoryAgentTools @Tool 方法
系統 SHALL 提供 `@Tool checkInventory(productName)` 方法，含冪等 key 保護、SSE 事件發佈與 Tracing span。

#### Scenario: 成功查詢庫存
- **WHEN** LLM 呼叫 `checkInventory("iPhone 15")`，inventory-service 回傳 quantity=10
- **THEN** 方法回傳 `"iPhone 15 庫存：10 件，有貨"`，並發佈 STARTED → SUCCEEDED 兩個 AgentStepEvent，Redis 寫入冪等 key

#### Scenario: 冪等保護：相同 runId + productName 不重複呼叫
- **WHEN** 相同 step key（`step:{runId}:checkInventory:{productName}`）已存在於 Redis
- **THEN** 直接回傳快取結果，不呼叫 InventoryServiceClient，不發佈事件

#### Scenario: 查詢失敗時回傳錯誤字串
- **WHEN** InventoryServiceClient 拋出 `ServiceBusinessException`
- **THEN** 回傳 `"BUSINESS_ERROR|查詢庫存失敗：{message}"`，發佈 FAILED AgentStepEvent，不拋例外

#### Scenario: 查詢暫時失敗時回傳 TRANSIENT_ERROR
- **WHEN** InventoryServiceClient 拋出 `ServiceTransientException`
- **THEN** 回傳 `"TRANSIENT_ERROR|查詢庫存失敗：{message}"`，發佈 FAILED AgentStepEvent

---

### Requirement: InventoryAgent Sub-agent @Tool 介面
系統 SHALL 提供 `InventoryAgent` @Component，其 `@Tool ask(productName)` 方法作為 Orchestrator 呼叫庫存子 Agent 的介面。

#### Scenario: Orchestrator 呼叫 ask 查詢庫存
- **WHEN** Orchestrator LLM 以 `productName="iPhone 15"` 呼叫 `InventoryAgent.ask`
- **THEN** InventoryAgent 的 ChatClient 以 productName 為 user message 呼叫 `checkInventory`，回傳自然語言結果

#### Scenario: 商品不存在時回傳清楚訊息
- **WHEN** Orchestrator 以不存在的商品名稱呼叫 `InventoryAgent.ask`
- **THEN** 回傳描述商品不存在的自然語言字串（含 `BUSINESS_ERROR|` 前綴透傳）

---

### Requirement: OrderAgent Sub-agent @Tool 介面
系統 SHALL 提供 `OrderAgent` @Component，其 `@Tool ask(task, userId)` 方法作為 Orchestrator 呼叫訂單子 Agent 的介面。

#### Scenario: Orchestrator 呼叫 ask 建立訂單
- **WHEN** Orchestrator LLM 以 `task="建立 iPhone 15 訂單數量 1"`, `userId="U001"` 呼叫 `OrderAgent.ask`
- **THEN** OrderAgent 的 ChatClient 收到包含 userId 的 user message，呼叫 `createOrder`，回傳訂單建立結果

#### Scenario: userId 正確組入 user message
- **WHEN** `OrderAgent.ask("建立訂單", "U001")` 被呼叫
- **THEN** 傳入 sub-agent ChatClient 的 user message 包含 `[客戶 ID: U001]` 前綴
