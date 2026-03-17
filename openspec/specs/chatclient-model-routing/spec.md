## ADDED Requirements

### Requirement: 依 model 識別字路由至對應 ChatClient
系統 SHALL 根據請求中的 `model` 欄位，從 `ChatClientFactory` 取得對應的 `ChatClient` 實例。

#### Scenario: model 為 "google" 時回傳 Gemini ChatClient
- **WHEN** `ChatClientFactory.getClient("google")` 被呼叫
- **THEN** 系統 SHALL 回傳綁定 `gemini-2.5-flash` 的 ChatClient

#### Scenario: model 為 null 時預設回傳 Gemini ChatClient
- **WHEN** `ChatClientFactory.getClient(null)` 被呼叫
- **THEN** 系統 SHALL 回傳綁定 `gemini-2.5-flash` 的 ChatClient（與 "google" 相同行為）

#### Scenario: model 為 "anthropic" 時回傳 Claude ChatClient
- **WHEN** `ChatClientFactory.getClient("anthropic")` 被呼叫
- **THEN** 系統 SHALL 回傳綁定 `claude-haiku-4-5-20251001` 的 ChatClient

#### Scenario: model 為未知值時拋出例外
- **WHEN** `ChatClientFactory.getClient("unknown-model")` 被呼叫
- **THEN** 系統 SHALL 拋出 `IllegalArgumentException`，訊息包含不支援的 model 值

---

### Requirement: ChatClient 實例預建並共用
系統 SHALL 在 Spring Context 啟動時預建兩個 ChatClient 實例，不在每次請求時重新建立。

#### Scenario: 兩個 ChatClient 均套用相同 system prompt 與 advisors
- **WHEN** Spring Context 初始化完成
- **THEN** `geminiChatClient` 與 `anthropicChatClient` 均 SHALL 設有相同的 defaultSystem prompt、`VectorStoreChatMemoryAdvisor` 及 `MessageChatMemoryAdvisor`
