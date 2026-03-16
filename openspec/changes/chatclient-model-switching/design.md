## Context

`agent-service` 目前的 `SpringAiConfig` 只建立單一 `ChatClient` bean，注入到 `AgentRunExecutor`。`AgentRunExecutor` 透過 `@Async` 執行每一次 Agent Run，呼叫 `chatClient.prompt()...call()` 與 LLM 互動。要支援多模型，需要在不破壞現有 SSE 流程與 tracing 架構的前提下，讓每次執行可以動態選擇模型。

## Goals / Non-Goals

**Goals:**
- API 層新增可選 `model` 欄位，預設 `"google"`（向下相容）
- 工廠模式封裝模型路由邏輯，`AgentRunExecutor` 不直接耦合具體模型
- 兩個 `ChatClient` bean 預先建立（共用 system prompt + advisors），不 per-request 建立

**Non-Goals:**
- 支援超過兩個模型
- 每個 conversationId 綁定固定模型
- Anthropic 失敗時自動 fallback 至 Google
- 動態設定新模型（不改 code 即可擴充）

## Decisions

### D1：工廠持有預建實例，而非 per-request 建立

`ChatClient` 本身 stateless，`VectorStoreChatMemoryAdvisor` 與 `MessageChatMemoryAdvisor` 可共用；per-request 建立無必要且浪費。兩個 bean 在 Spring Context 啟動時建立，factory 持有引用。

替代方案：每次 `getClient()` 時 `ChatClient.builder()` 重新建立 → 多餘 overhead，排除。

### D2：`ChatClientFactory` 設計為介面

方便 `AgentRunExecutorTest` mock，符合 DIP。實作 `ChatClientFactoryImpl` 以 `@Qualifier` 注入兩個具名 bean。

替代方案：直接在 `AgentRunExecutor` 注入兩個 `ChatClient` 加 if-else → 職責不清、難測試，排除。

### D3：`model` 為 `String` 而非 enum

目前只做測試用途，保持簡單。未來若需嚴格驗證再升級為 enum。

### D4：未知 model 值在 factory 層拋例外

`ChatClientFactory.getClient()` 對未知值拋 `IllegalArgumentException`，由 `@RestControllerAdvice` 統一對應 400 回應。不在 DTO validation 層處理，避免 enum/whitelist 重複維護。

## Risks / Trade-offs

- **Anthropic API Key 未設定** → Spring Boot 啟動失敗（fail-fast）。可透過 `required = false` 降級，但目前測試環境可接受強制設定。
- **兩個 ChatClient 共用 advisors** → advisors 若有 model-specific 行為差異（目前無），需拆分。現在可接受。
- **model 欄位為自由字串** → 未來 API consumer 拼錯會拿到 400，無 IDE 提示。範圍小可接受。

## Migration Plan

1. 新增 Anthropic starter 依賴與 `application.yml` 設定（需 `ANTHROPIC_API_KEY` 環境變數）
2. `SpringAiConfig` 拆 bean，`ChatClientFactory` 新增
3. `ChatRequest`、`AgentRunService`、`AgentRunExecutor` 修改
4. 測試通過後部署，現有無 `model` 欄位的 client 不受影響（null → Google）

回滾：移除 Anthropic 依賴與設定，還原以上三個類別。
