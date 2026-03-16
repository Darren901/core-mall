# ChatClient 模型切換設計文件

**日期**：2026-03-16
**服務**：agent-service
**目標**：支援 per-request 指定 LLM 模型（Google Gemini / Anthropic Claude），使用工廠模式切換

---

## 背景

目前 `AgentRunExecutor` 直接注入單一 `ChatClient`（固定使用 Google Gemini 2.5 Flash）。
為了測試多模型效果，需要在 API 請求層允許 caller 指定使用哪個模型。

---

## 需求

- 呼叫 `POST /agent/chat` 時可選填 `model` 欄位
- `model` 為 null 時預設使用 Google Gemini（維持現有行為相容）
- 支援兩個固定模型：
  - `"google"` → `gemini-2.5-flash`
  - `"anthropic"` → `claude-haiku-4-5-20251001`
- 其他值拋出 `IllegalArgumentException`

---

## 架構設計

### 請求流程

```
POST /agent/chat
  { "message": "...", "model": "anthropic" }
        ↓
AgentChatController
        ↓
AgentRunService.initRun(request)
  → 把 model 往下傳
        ↓
AgentRunExecutor.execute(runId, userId, message, model)
        ↓
ChatClientFactory.getClient(model)
  → 回傳對應 ChatClient 實例
        ↓
chatClient.prompt().user(message)...call()
```

### 元件設計

#### `ChatRequest`（修改）

```java
public record ChatRequest(
    @NotBlank String message,
    String model   // nullable，null 視同 "google"
) {}
```

#### `ChatClientFactory`（新增介面）

```java
public interface ChatClientFactory {
    ChatClient getClient(String model);
}
```

#### `ChatClientFactoryImpl`（新增實作）

- 注入 `geminiChatClient` 與 `anthropicChatClient`（以 `@Qualifier` 區分）
- `model` 為 null 或 `"google"` → 回傳 `geminiChatClient`
- `model` 為 `"anthropic"` → 回傳 `anthropicChatClient`
- 其他值 → 拋出 `IllegalArgumentException("不支援的模型: " + model)`

#### `SpringAiConfig`（修改）

將現有單一 `chatClient` bean 拆成兩個具名 bean：

| Bean 名稱 | 模型 | 備註 |
|---|---|---|
| `geminiChatClient` | `gemini-2.5-flash` | 現有邏輯搬移，相同 system prompt + advisors |
| `anthropicChatClient` | `claude-haiku-4-5-20251001` | 新增，相同 system prompt + advisors |

兩個 bean 共用：
- 相同 `defaultSystem`（system prompt）
- 相同 advisors：`VectorStoreChatMemoryAdvisor` + `MessageChatMemoryAdvisor`

#### `AgentRunExecutor`（修改）

```java
// 注入改為 ChatClientFactory
public AgentRunExecutor(ChatClientFactory chatClientFactory, ...)

// execute 加 model 參數
public void execute(String runId, String userId, String userMessage, String model)

// 內部取 client
ChatClient client = chatClientFactory.getClient(model);
client.prompt().user(userMessage)...
```

#### `AgentRunService`（修改）

`initRun()` 將 `request.model()` 傳給 `AgentRunExecutor.execute()`。

---

## 相依性變更

`agent-service/pom.xml` 新增：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>
```

`application.yml` 新增：

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-haiku-4-5-20251001
```

---

## 錯誤處理

| 情境 | 行為 |
|---|---|
| `model` 為 null | 使用 `"google"` 預設值 |
| `model` 為未知值 | `ChatClientFactory` 拋 `IllegalArgumentException` |
| Anthropic API Key 未設定 | Spring Boot 啟動失敗（fail-fast） |

---

## 測試策略

- `ChatClientFactoryImplTest`：單元測試，驗證 null / "google" / "anthropic" / 未知值的路由行為
- `AgentRunExecutorTest`：mock `ChatClientFactory`，驗證 `execute()` 正確呼叫 factory
- `AgentRunServiceTest`：驗證 model 欄位正確從 request 傳遞到 executor

---

## 不在範圍內

- 動態新增模型（目前硬編碼兩個，未來若有需要再抽設定檔）
- 每個 conversationId 綁定固定模型
- 模型 fallback 機制（Anthropic 失敗不自動切 Google）
