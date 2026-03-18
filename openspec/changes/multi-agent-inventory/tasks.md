## 1. 基礎建設

- [x] 1.1 `application-dev.yml` 新增 `inventory-service.base-url: http://localhost:8084`
- [x] 1.2 新增 `dto/InventoryResult.java`（Java Record：productName, quantity）

## 2. InventoryServiceClient

- [x] 2.1 實作 `client/InventoryServiceClient.java`，照 OrderServiceClient 模式（WebClient、retry policy、toFriendlyError）
- [x] 2.2 `WebClientConfig` 新增 `inventoryServiceWebClient` Bean，讀取 `inventory-service.base-url`
- [x] 2.3 補齊 `InventoryServiceClient` 單元測試（MockWebServer：200、404、500）

## 3. InventoryAgentTools

- [x] 3.1 實作 `tool/InventoryAgentTools.java`，`@Tool checkInventory(productName)`，含冪等 key、SSE 事件、Tracing span
- [x] 3.2 補齊 `InventoryAgentTools` 單元測試（成功、冪等 cache hit、BUSINESS_ERROR、TRANSIENT_ERROR）

## 4. Sub-agent ChatClient Beans

- [x] 4.1 使用 `prompt-engineering` skill 設計 InventoryAgent system prompt（few-shot：有貨、無此商品）
- [x] 4.2 使用 `prompt-engineering` skill 設計 OrderAgent system prompt（few-shot：建立、查詢、取消）
- [x] 4.3 使用 `prompt-engineering` skill 改寫 Orchestrator system prompt（路由邏輯、管理員情境、錯誤前綴解讀）
- [x] 4.4 `SpringAiConfig` 新增 `inventoryAgentChatClient` Bean（stateless）
- [x] 4.5 `SpringAiConfig` 新增 `orderAgentChatClient` Bean（stateless）
- [x] 4.6 `MockAiConfig` 新增對應的 loadtest profile mock beans

## 5. InventoryAgent 與 OrderAgent

- [x] 5.1 新增 `agent/` package
- [x] 5.2 實作 `agent/InventoryAgent.java`：`@Tool ask(productName)` 呼叫 inventoryAgentChatClient + InventoryAgentTools
- [x] 5.3 實作 `agent/OrderAgent.java`：`@Tool ask(task, userId)` 組裝 user message（加 `[客戶 ID: {userId}]` 前綴）後呼叫 orderAgentChatClient + OrderAgentTools
- [x] 5.4 補齊 `InventoryAgent` 單元測試（mock ChatClient，驗證 user message 組裝、回傳透傳）
- [x] 5.5 補齊 `OrderAgent` 單元測試（mock ChatClient，驗證 userId 前綴、回傳透傳）

## 6. AgentRunExecutor 切換

- [x] 6.1 `AgentRunExecutor` 注入 `InventoryAgent` + `OrderAgent`，移除 `OrderAgentTools` 直接注入
- [x] 6.2 `AgentRunExecutor.execute()` 改為 `.tools(inventoryAgent, orderAgent)`
- [x] 6.3 更新 `AgentRunExecutor` 單元測試（mock inventoryAgent + orderAgent，驗證 tools 綁定正確）

## 7. 驗證

- [x] 7.1 `mvn clean verify -pl agent-service` 確認覆蓋率 Line ≥ 80%、Branch ≥ 80%
- [ ] 7.2 啟動 infra（docker compose）+ inventory-service + agent-service，手動測試「查庫存 → 下單」流程
