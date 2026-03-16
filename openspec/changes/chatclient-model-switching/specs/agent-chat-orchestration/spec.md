## MODIFIED Requirements

### Requirement: LLM 驅動的 Function Calling
系統必須使用真實 LLM（透過 Spring AI ChatClient，依請求指定的模型，預設 Gemini 2.5 Flash）根據自然語言輸入決定呼叫哪個工具。明確禁止使用關鍵字比對。

#### Scenario: LLM 選擇 createOrder 工具
- **WHEN** 用戶訊息表達建立訂單的意圖（例如「幫我訂5個蘋果」）
- **THEN** ChatClient 應以 LLM 萃取的參數呼叫 `createOrder` @Tool 方法

#### Scenario: LLM 選擇 updateOrder 工具
- **WHEN** 用戶訊息表達更新現有訂單的意圖
- **THEN** ChatClient 應呼叫 `updateOrder` @Tool 方法

#### Scenario: LLM 選擇 cancelOrder 工具
- **WHEN** 用戶訊息表達取消訂單的意圖
- **THEN** ChatClient 應呼叫 `cancelOrder` @Tool 方法

#### Scenario: LLM 選擇 getOrderStatus 工具
- **WHEN** 用戶訊息詢問訂單狀態
- **THEN** ChatClient 應呼叫 `getOrderStatus` @Tool 方法

#### Scenario: 多步驟執行
- **WHEN** 用戶訊息隱含多個連續操作（例如「訂3個橘子然後查狀態」）
- **THEN** ChatClient 應依序呼叫多個工具，每個工具呼叫產生獨立的 AgentStep

## ADDED Requirements

### Requirement: ChatRequest 支援可選 model 欄位
系統 SHALL 接受 `POST /api/v1/agent/chat` 請求 body 中的可選 `model` 欄位，並將其傳遞至執行層。

#### Scenario: 請求帶有 model 欄位
- **WHEN** 客戶端傳送 `{"message": "...", "model": "anthropic"}` 至 `POST /api/v1/agent/chat`
- **THEN** 系統 SHALL 使用 `anthropic` 對應的 ChatClient 執行此次 Agent Run

#### Scenario: 請求未帶 model 欄位
- **WHEN** 客戶端傳送 `{"message": "..."}` 至 `POST /api/v1/agent/chat`（`model` 為 null）
- **THEN** 系統 SHALL 使用 Google Gemini ChatClient 執行此次 Agent Run（向下相容）

#### Scenario: 請求帶有未知 model 值
- **WHEN** 客戶端傳送 `{"message": "...", "model": "gpt-4"}` 至 `POST /api/v1/agent/chat`
- **THEN** 系統 SHALL 回傳 HTTP 400 錯誤回應
