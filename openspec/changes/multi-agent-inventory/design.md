## Context

目前 `AgentRunExecutor` 採單一 Agent 模式：一個 LLM 直接持有 `OrderAgentTools`（4 個工具）。所有意圖判斷由同一個 LLM 完成，系統無法查詢庫存，且無法擴充更多專業領域。

本次引入 **Orchestrator + Sub-agent 模式**：Orchestrator LLM 負責意圖路由，`InventoryAgent` 和 `OrderAgent` 各自是獨立的 `@Component`，持有自己的 ChatClient 與工具集，並以 `@Tool` 方法暴露給 Orchestrator 呼叫。

業務情境：電商後台管理員操作，userId 指客戶 ID。

---

## Goals / Non-Goals

**Goals:**
- Orchestrator 透過 `@Tool ask(...)` 路由任務給 InventoryAgent / OrderAgent
- 每個 sub-agent 有獨立 system prompt + few-shot，無對話記憶（stateless）
- 錯誤前綴（`TRANSIENT_ERROR|` / `BUSINESS_ERROR|`）從 tool 層透傳至 Orchestrator，不在中間翻譯
- 外部 API（`POST /api/v1/agent/chat`）介面不變
- 覆蓋率維持 Line ≥ 80%、Branch ≥ 80%

**Non-Goals:**
- 修改 inventory-service 或 order-service
- Sub-agent 之間直接通訊（點對點）
- Parallel fan-out（多 sub-agent 同時執行）
- inventory-service 走 Eureka service discovery（維持直接 URL，與 order-service 一致）

---

## Decisions

### D1：Sub-agent 以 `@Tool` 方法暴露給 Orchestrator

**決定：** `InventoryAgent` 和 `OrderAgent` 是 Spring `@Component`，各自有一個 `@Tool` 方法。Orchestrator 在 `AgentRunExecutor` 中以 `.tools(inventoryAgent, orderAgent)` 綁定。

**替代方案考慮：**
- 直接在 Orchestrator 掛全部工具（現狀）→ 無法分離職責，無法練習 multi-agent
- 自訂 AgentRouter 類別做程式碼層路由 → 繞開 LLM 決策，不是真正的 multi-agent

**選擇原因：** 讓 LLM 看到的工具是「詢問哪個 agent」而非「直接呼叫底層操作」，Orchestrator 做意圖路由、sub-agent 做具體執行，符合 Orchestrator pattern 語意。

---

### D2：Sub-agent 採結構化參數，強制 Orchestrator 明確填寫關鍵欄位

**決定：**
```java
// InventoryAgent — 與使用者身份無關
@Tool("查詢商品庫存數量")
String ask(@ToolParam("要查詢的商品名稱") String productName)

// OrderAgent — 客戶 ID 必須明確傳入
@Tool("執行訂單操作（建立、更新、取消、查詢）")
String ask(@ToolParam("任務描述") String task,
           @ToolParam("客戶 ID") String userId)
```

**替代方案考慮：**
- 純 String query（讓 Orchestrator 自己把 context 組入）→ userId 可能被遺漏
- 在 @Tool 方法多一個 `additionalInstructions` 參數讓 Orchestrator 動態傳 prompt → 過度工程，增加 prompt injection 風險

**選擇原因：** 結構化參數讓 LLM 必須明確填入 userId，降低資訊遺漏機率，同時保持 tool signature 清晰可讀。

---

### D3：OrderAgent 內部將 userId 組入 user message 傳給 sub-agent ChatClient

**決定：** `OrderAgent.ask(task, userId)` 方法在 Java 層組合 user message：
```
"[客戶 ID: {userId}] {task}"
```
OrderAgent ChatClient 的 system prompt 包含解析規則，OrderAgent LLM 從 user message 提取 userId 後呼叫 `OrderAgentTools` 的對應方法。

**選擇原因：** Sub-agent 的 system prompt 是靜態 Bean，Java 層負責組裝動態 context，不需要動態修改 system prompt，架構更簡單。

---

### D4：Sub-agent ChatClient 不掛 memory advisor（stateless）

**決定：** Sub-agent ChatClient 只用 `ChatClient.builder(model).defaultSystem(...).build()`，不加 `MessageChatMemoryAdvisor` 或 `VectorStoreChatMemoryAdvisor`。

**選擇原因：** Sub-agent 每次呼叫是單次任務，context 已透過參數傳入，對話歷史由 Orchestrator 層維護即可。加 memory 會浪費 token 且引入不必要的 Redis 依賴。

---

### D5：InventoryServiceClient 採直接 URL，不走 Eureka

**決定：** `application-dev.yml` 新增 `inventory-service.base-url: http://localhost:8084`，`InventoryServiceClient` 照 `OrderServiceClient` 模式實作。

**選擇原因：** 與現有 `OrderServiceClient` 保持一致（`order-service.base-url: http://localhost:8082`），減少架構跳躍，練習專案不需要 Eureka load balancing。

---

## Risks / Trade-offs

- **LLM 呼叫次數增加** → 每次任務最多 3 次 LLM 呼叫（Orchestrator + sub-agent + tool result）。
  緩解：dev 環境可觀測 Zipkin trace；若成本過高可優化 sub-agent prompt 減少 token 使用。

- **Orchestrator 可能傳入錯誤的 userId** → LLM 語意理解失誤。
  緩解：Orchestrator system prompt 明確說明 userId 是客戶 ID，並加 few-shot 範例。

- **Sub-agent ChatClient Bean 數量增加** → Spring context 有更多 ChatClient bean，需明確 `@Qualifier`。
  緩解：以 `inventoryAgentChatClient` / `orderAgentChatClient` 命名區隔。

---

## Migration Plan

1. 新增檔案（InventoryServiceClient、InventoryAgentTools、InventoryAgent、OrderAgent）
2. 修改 SpringAiConfig（新增 sub-agent beans、更新 Orchestrator system prompt）
3. 修改 AgentRunExecutor（切換 tools 綁定）
4. 修改 application-dev.yml（新增 inventory-service.base-url）
5. 補齊單元測試至覆蓋率門檻

**Rollback：** 若 Orchestrator 表現不穩定，可在 `AgentRunExecutor` 改回 `.tools(orderAgentTools)` 恢復單一 Agent 模式，sub-agent 程式碼可保留不影響執行。

---

## Open Questions

- Sub-agent system prompt 的 few-shot 範例品質直接影響路由準確度，需在 `prompt-engineering` skill 輔助下仔細設計。
- `application-test.yml` 的 mock 設定是否需要同步新增 `inventory-service.base-url`？（可在實作測試時確認）
