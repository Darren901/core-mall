## Why

目前 agent-service 是單一 Agent 模式：一個 LLM 持有全部訂單工具，無法查詢庫存，也無法練習 Multi-Agent Orchestrator 架構。本次變更引入 Orchestrator + Sub-agent 模式，讓管理員能在同一對話中先查庫存、再決定是否下單，同時透過實作展示真實工業級 Multi-Agent 設計模式。

## What Changes

- **新增** `InventoryServiceClient`：以 WebClient 呼叫 inventory-service `GET /api/inventory/{productName}`
- **新增** `InventoryAgentTools`：`@Tool checkInventory(productName)`，含冪等 key、SSE 事件、Tracing span
- **新增** `InventoryAgent`：`@Component`，持有 stateless ChatClient + few-shot，`@Tool ask(productName)` 供 Orchestrator 呼叫
- **新增** `OrderAgent`：`@Component`，持有 stateless ChatClient + few-shot，`@Tool ask(task, userId)` 供 Orchestrator 呼叫，內部使用現有 `OrderAgentTools`
- **新增** `agent/` package 放置兩個 Sub-agent
- **修改** `AgentRunExecutor`：`.tools(inventoryAgent, orderAgent)` 取代 `.tools(orderAgentTools)`
- **修改** `SpringAiConfig`：新增 sub-agent ChatClient beans（stateless）；Orchestrator system prompt 改為路由邏輯
- **修改** `application-dev.yml`：新增 `inventory-service.base-url`

## Capabilities

### New Capabilities
- `inventory-agent-integration`: 庫存查詢工具鏈（InventoryServiceClient + InventoryAgentTools + InventoryAgent）及 OrderAgent 封裝，供 Orchestrator 呼叫的兩個 sub-agent @Tool 介面

### Modified Capabilities
- `agent-chat-orchestration`: LLM 工具綁定從直接持有 OrderAgentTools 改為 Orchestrator 模式（持有 InventoryAgent + OrderAgent 兩個 sub-agent），Orchestrator 透過結構化參數路由任務

## Impact

- **agent-service**：新增 4 個 Java 檔案，修改 3 個現有檔案
- **inventory-service**：無修改（使用現有 REST API）
- **外部 API**：無變更（`POST /api/v1/agent/chat` 介面不變）
- **LLM 呼叫次數**：每次任務從 1 次增為最多 3 次（Orchestrator + sub-agent + tool）
- **測試**：需補齊 InventoryServiceClient、InventoryAgentTools、InventoryAgent、OrderAgent 的單元測試
