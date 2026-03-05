## ADDED Requirements

### Requirement: 立即回傳接受回應
系統必須在收到聊天請求後立即回傳接受回應，不等待訂單操作完成。

#### Scenario: 聊天請求被接受
- **WHEN** 客戶端傳送有效訊息至 `POST /api/v1/agent/chat`
- **THEN** 系統應在任何 LLM 或 tool 執行開始前回傳 HTTP 202 及 `sessionId` 與 `runId`

---

### Requirement: LLM 驅動的 Function Calling
系統必須使用真實 LLM（透過 Spring AI ChatClient 接 Gemini）根據自然語言輸入決定呼叫哪個工具。明確禁止使用關鍵字比對。

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

---

### Requirement: SSE 串流進度事件
系統必須透過 Server-Sent Events 將 Agent 執行進度串流傳送給客戶端。

#### Scenario: 客戶端訂閱串流
- **WHEN** 客戶端連線至 `GET /api/v1/agent/sessions/{sessionId}/stream`
- **THEN** 系統應透過 Flux 回傳 `text/event-stream` 回應

#### Scenario: 發送 Step 事件
- **WHEN** 每個工具被呼叫並完成（或失敗）
- **THEN** SSE 串流應發送包含 step 名稱、狀態（STARTED / SUCCEEDED / FAILED）及相關 metadata 的事件

#### Scenario: 發送 Run 完成事件
- **WHEN** 所有工具呼叫完成且 LLM 產生最終回應
- **THEN** SSE 串流應發送 run-completed 事件

---

### Requirement: Agent Step 稽核記錄
系統必須持久化每個 Agent step 的狀態、時間與錯誤資訊，以支援可觀測性與重試。

#### Scenario: 工具呼叫時建立 Step 記錄
- **WHEN** 工具即將被執行
- **THEN** 系統應非同步持久化狀態為 STARTED 的 AgentStep 記錄

#### Scenario: 完成時更新 Step 記錄
- **WHEN** 工具執行完成或失敗
- **THEN** 系統應非同步更新 AgentStep 記錄為 SUCCEEDED 或 FAILED 狀態，失敗時附上錯誤訊息

---

### Requirement: Step 層級冪等性
系統必須在相同 step 被重試時防止工具重複執行。

#### Scenario: 工具執行前檢查冪等鍵
- **WHEN** 工具即將執行
- **THEN** 系統應在呼叫 order-service 前同步檢查 Redis 中的 step 冪等鍵

#### Scenario: 已成功的 Step 回傳快取結果
- **WHEN** Redis 中存在該 step 的冪等鍵且結果為 SUCCEEDED
- **THEN** 系統應直接回傳快取結果，不再呼叫 order-service

#### Scenario: 冪等鍵以 Run 為範圍
- **WHEN** 相同工具在兩個不同的 run 中被呼叫
- **THEN** 每個 run 應有各自的冪等鍵範圍，各自獨立執行
