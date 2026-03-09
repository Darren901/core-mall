## ADDED Requirements

### Requirement: Agent 保留超過視窗限制的長期對話記憶
系統 SHALL 透過 `VectorStoreChatMemoryAdvisor` 將所有對話訊息以 embedding 形式儲存於 `RedisVectorStore`，使 Agent 能在短期視窗（20 條）之外，語意搜尋並引用更早期的對話內容。

#### Scenario: 引用超出短期視窗的早期訊息
- **WHEN** 用戶已有超過 20 輪的對話歷史，且在新的 run 中詢問早期對話提及的資訊
- **THEN** Agent SHALL 透過語意搜尋取出相關歷史訊息並正確回應，不需用戶重複提供

#### Scenario: 長期記憶以 userId 隔離
- **WHEN** 用戶 U001 發起 run
- **THEN** 系統 SHALL 只搜尋 U001 的歷史向量，不混入其他用戶的記憶

#### Scenario: 每次 run 結束自動存入長期記憶
- **WHEN** Agent 完成一次 run（LLM 回應完畢）
- **THEN** 系統 SHALL 將本次對話訊息 embed 並寫入 RedisVectorStore，key prefix 為 `ltm:`

---

### Requirement: 雙層記憶架構並存
系統 SHALL 同時維持短期近因層（`MessageChatMemoryAdvisor`，最近 20 條）與長期語意層（`VectorStoreChatMemoryAdvisor`，所有歷史），兩者在同一次 run 中均生效。

#### Scenario: 近因層保障最近對話連貫性
- **WHEN** 用戶在同一對話內連續發出語意不相關的訊息（例如「好的謝謝」接在「建立訂單」之後）
- **THEN** 系統 SHALL 透過近因層確保 Agent 能感知剛發生的對話，不依賴語意搜尋

#### Scenario: 長期層注入在系統訊息，近因層注入在訊息結構
- **WHEN** Agent 處理一次 run
- **THEN** 長期記憶 SHALL 以文字形式注入 system message，近因記憶 SHALL 以 USER/ASSISTANT message 結構注入
