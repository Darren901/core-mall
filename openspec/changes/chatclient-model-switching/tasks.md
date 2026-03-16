## 1. 相依性與設定

- [ ] 1.1 `agent-service/pom.xml` 新增 `spring-ai-starter-model-anthropic` 依賴
- [ ] 1.2 `application.yml` 新增 Anthropic API Key 設定（`${ANTHROPIC_API_KEY}`）與模型（`claude-haiku-4-5-20251001`）
- [ ] 1.3 `application-test.yml` 新增 Anthropic mock API Key（測試環境用）

## 2. ChatClientFactory 實作

- [ ] 2.1 建立 `ChatClientFactory` 介面（`getClient(String model)` 方法）
- [ ] 2.2 建立 `ChatClientFactoryImpl`：注入 `@Qualifier("geminiChatClient")` 與 `@Qualifier("anthropicChatClient")`，實作路由邏輯（null/"google" → gemini，"anthropic" → anthropic，其他 → IllegalArgumentException）
- [ ] 2.3 撰寫 `ChatClientFactoryImplTest`：驗證 null、"google"、"anthropic"、未知值四個 case

## 3. SpringAiConfig 重構

- [ ] 3.1 將現有 `chatClient` bean 重命名為 `geminiChatClient`（注入 `GoogleGeminiChatModel`，保留相同 system prompt + advisors）
- [ ] 3.2 新增 `anthropicChatClient` bean（注入 `AnthropicChatModel`，套用相同 system prompt + advisors）
- [ ] 3.3 新增 `ChatClientFactoryImpl` bean

## 4. 請求鏈修改

- [ ] 4.1 `ChatRequest` 新增可選 `model` 欄位（`String model`，nullable）
- [ ] 4.2 `AgentRunService.initRun()` 將 `request.model()` 傳給 `AgentRunExecutor.execute()`
- [ ] 4.3 `AgentRunExecutor` 注入改為 `ChatClientFactory`，`execute()` 加 `model` 參數，內部改用 `chatClientFactory.getClient(model)`

## 5. 測試補齊

- [ ] 5.1 `AgentRunExecutorTest`：mock `ChatClientFactory`，驗證 execute() 正確以 model 呼叫 factory
- [ ] 5.2 `AgentRunServiceTest`：驗證 model 欄位從 request 正確傳遞到 executor
- [ ] 5.3 執行 `mvn clean verify` 確認 JaCoCo 覆蓋率 Line/Branch ≥ 80%
