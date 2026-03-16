## Why

`AgentRunExecutor` 目前硬綁定單一 `ChatClient`（Google Gemini 2.5 Flash），無法在 runtime 切換 LLM 提供商。為了驗證不同模型在訂單管理 Agent 場景的表現差異，需要支援 per-request 指定模型。

## What Changes

- `ChatRequest` 新增可選 `model` 欄位（null 預設 Google）
- 新增 `ChatClientFactory` 介面與 `ChatClientFactoryImpl`，依 model 識別字回傳對應 `ChatClient`
- `SpringAiConfig` 拆分為兩個具名 `ChatClient` bean：`geminiChatClient`、`anthropicChatClient`
- `AgentRunExecutor.execute()` 加 `model` 參數，從 factory 取 client
- `AgentRunService.initRun()` 將 `request.model()` 往下傳遞
- `agent-service/pom.xml` 新增 `spring-ai-starter-model-anthropic` 依賴
- `application.yml` 新增 Anthropic API Key 設定

## Capabilities

### New Capabilities

- `chatclient-model-routing`：per-request LLM 模型路由，支援 "google"（Gemini 2.5 Flash）與 "anthropic"（Claude Haiku 4.5），null 預設 Google，未知值返 IllegalArgumentException

### Modified Capabilities

- `agent-chat-orchestration`：`ChatRequest` schema 新增 `model` 欄位，`AgentRunExecutor.execute()` 簽名加入 `model` 參數

## Impact

- **API**：`POST /agent/chat` request body 新增可選 `model` 欄位（向下相容）
- **程式碼**：`AgentRunExecutor`、`AgentRunService`、`SpringAiConfig`、`ChatRequest`
- **新增檔案**：`ChatClientFactory`、`ChatClientFactoryImpl`
- **相依性**：`spring-ai-starter-model-anthropic`、環境變數 `ANTHROPIC_API_KEY`
