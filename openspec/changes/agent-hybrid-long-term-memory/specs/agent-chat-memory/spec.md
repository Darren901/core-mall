## MODIFIED Requirements

### Requirement: Agent 保留跨 run 的對話記憶
系統 SHALL 在同一 userId 的多次 `POST /agent/runs` 之間保留對話歷史，使 Agent 能在後續 run 中引用先前的對話內容（例如用戶身份、訂單操作記錄）。記憶採雙層架構：近因層以 `userId` 為 `conversationId` 儲存於 Redis（JSON list），保留最近 20 條訊息；長期層以 embedding 儲存於 RedisVectorStore，保留所有歷史。

#### Scenario: 跨 run 引用先前訊息
- **WHEN** 用戶在第一個 run 說「我是用戶 U001」，並在第二個 run 說「幫我查最新訂單」
- **THEN** Agent 在第二個 run 中能識別「用戶 U001」，無需用戶重複提供身份

#### Scenario: 近因視窗限制超過 20 條時截斷舊訊息
- **WHEN** 某 userId 的近因記憶已有 20 條訊息，且新的 run 產生新訊息
- **THEN** 系統 SHALL 從近因層移除最舊的訊息，保持最多 20 條；舊訊息仍保留於長期向量層

#### Scenario: 清除記憶同時清除短期與長期
- **WHEN** 呼叫 `DELETE /agent/memory/{userId}`
- **THEN** 系統 SHALL 同時清除該 userId 的近因記憶（Redis JSON list）與長期向量記憶（RedisVectorStore），回傳 `204 No Content`
