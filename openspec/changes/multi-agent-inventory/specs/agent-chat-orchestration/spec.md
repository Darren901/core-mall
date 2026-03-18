## MODIFIED Requirements

### Requirement: LLM 驅動的 Function Calling
系統必須使用真實 LLM（透過 Spring AI ChatClient，依請求指定的模型，預設 Gemini 2.5 Flash）根據自然語言輸入決定呼叫哪個工具。明確禁止使用關鍵字比對。

Orchestrator LLM 的工具集為兩個 Sub-agent（`InventoryAgent.ask` 和 `OrderAgent.ask`），不直接持有底層操作工具。

#### Scenario: LLM 路由庫存查詢至 InventoryAgent
- **WHEN** 管理員訊息表達查詢庫存意圖（例如「iPhone 有沒有貨」）
- **THEN** Orchestrator ChatClient SHALL 以商品名稱呼叫 `InventoryAgent.ask(productName)`

#### Scenario: LLM 路由訂單操作至 OrderAgent（建立）
- **WHEN** 管理員訊息表達建立訂單意圖（例如「幫客戶 U001 訂 1 個 iPhone 15」）
- **THEN** Orchestrator ChatClient SHALL 以任務描述與客戶 ID 呼叫 `OrderAgent.ask(task, userId)`

#### Scenario: LLM 路由訂單操作至 OrderAgent（查詢）
- **WHEN** 管理員訊息表達查詢訂單狀態意圖
- **THEN** Orchestrator ChatClient SHALL 呼叫 `OrderAgent.ask(task, userId)`

#### Scenario: LLM 先查庫存再下單（多步驟）
- **WHEN** 管理員訊息表達先確認庫存再建立訂單的意圖
- **THEN** Orchestrator ChatClient SHALL 先呼叫 `InventoryAgent.ask`，根據結果再決定是否呼叫 `OrderAgent.ask`

#### Scenario: 請求帶有 model 欄位（不變）
- **WHEN** 客戶端傳送 `{"message": "...", "model": "anthropic"}` 至 `POST /api/v1/agent/chat`
- **THEN** 系統 SHALL 使用 `anthropic` 對應的 ChatClient 執行此次 Agent Run

#### Scenario: 請求未帶 model 欄位（不變）
- **WHEN** 客戶端傳送 `{"message": "..."}` 至 `POST /api/v1/agent/chat`（`model` 為 null）
- **THEN** 系統 SHALL 使用 Google Gemini ChatClient 執行此次 Agent Run（向下相容）

#### Scenario: 請求帶有未知 model 值（不變）
- **WHEN** 客戶端傳送 `{"message": "...", "model": "gpt-4"}` 至 `POST /api/v1/agent/chat`
- **THEN** 系統 SHALL 回傳 HTTP 400 錯誤回應
