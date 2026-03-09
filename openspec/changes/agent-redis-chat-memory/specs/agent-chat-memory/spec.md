## ADDED Requirements

### Requirement: Agent 保留跨 run 的對話記憶
系統 SHALL 在同一 userId 的多次 `POST /agent/runs` 之間保留對話歷史，使 Agent 能在後續 run 中引用先前的對話內容（例如用戶身份、訂單操作記錄）。記憶以 `userId` 為 `conversationId` 儲存於 Redis Stack，最多保留最近 20 條訊息（MessageWindowChatMemory）。

#### Scenario: 跨 run 引用先前訊息
- **WHEN** 用戶在第一個 run 說「我是用戶 U001」，並在第二個 run 說「幫我查最新訂單」
- **THEN** Agent 在第二個 run 中能識別「用戶 U001」，無需用戶重複提供身份

#### Scenario: Window 限制超過 20 條時截斷舊訊息
- **WHEN** 某 userId 的對話歷史已有 20 條訊息，且新的 run 產生新訊息
- **THEN** 系統 SHALL 移除最舊的訊息，保持最多 20 條

---

### Requirement: 從 JWT Header 識別用戶
系統 SHALL 透過 Gateway 注入的 `X-User-Id` header 取得 userId 作為 conversationId，不接受 client 在 request body 自行傳入 userId。

#### Scenario: 正常帶有效 JWT
- **WHEN** request 帶有效 JWT，Gateway 注入 `X-User-Id: U001`
- **THEN** agent-service 以 `U001` 作為該次 run 的 conversationId

#### Scenario: 缺少 X-User-Id Header
- **WHEN** request 未帶 `X-User-Id` header（例如未經過 Gateway 直連）
- **THEN** 系統 SHALL 回傳 `400 Bad Request`

---

### Requirement: 清除用戶對話記憶
系統 SHALL 提供 `DELETE /agent/memory/{userId}` 端點，清除指定 userId 的所有對話歷史，使後續 run 從空白狀態開始。

#### Scenario: 成功清除記憶
- **WHEN** 呼叫 `DELETE /agent/memory/U001`，且 Redis 中有 U001 的對話記憶
- **THEN** 系統 SHALL 刪除 U001 的所有記憶，並回傳 `204 No Content`

#### Scenario: 清除不存在的記憶
- **WHEN** 呼叫 `DELETE /agent/memory/U999`，且 Redis 中無 U999 的對話記憶
- **THEN** 系統 SHALL 回傳 `204 No Content`（冪等，不報錯）

#### Scenario: 清除後下一個 run 從空白開始
- **WHEN** 清除 U001 的記憶後，U001 發起新的 run
- **THEN** Agent 不再有先前對話的上下文，視同全新用戶
