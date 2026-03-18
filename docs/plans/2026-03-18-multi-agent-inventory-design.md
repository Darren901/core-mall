# Multi-Agent Inventory Design

**日期：** 2026-03-18
**分支：** feature/multi-agent-inventory
**目標：** 將 agent-service 重構為 Orchestrator + Sub-agent 架構，新增庫存查詢能力

---

## 背景

目前 `AgentRunExecutor` 是單一 Agent 模式：一個 LLM 直接持有所有 `OrderAgentTools`。
本次重構為 **Orchestrator pattern**：Orchestrator LLM 路由任務給專業 sub-agent，每個 sub-agent 有自己的 system prompt、few-shot 與工具集。

業務情境：電商後台管理員操作，userId 指的是客戶 ID。

---

## 架構圖

```
AdminRequest
    ↓
AgentRunExecutor（@Async，不變）
    ↓
OrchestratorChatClient（LLM，含對話記憶）
  tools:
    ├─ InventoryAgent.ask(productName)
    │       → InventoryAgentChatClient（stateless LLM）
    │         system: 庫存查詢專家 + few-shot
    │         tool: InventoryAgentTools.checkInventory
    │
    └─ OrderAgent.ask(task, userId)
            → OrderAgentChatClient（stateless LLM）
              system: 訂單管理專家 + few-shot
              tools: OrderAgentTools（createOrder / updateOrder / cancelOrder / getOrderStatus）
```

### 範例執行流程

管理員：「幫客戶 U001 買 iPhone，先確認有沒有貨」

```
1. Orchestrator → askInventoryAgent("iPhone 15")
2.   InventoryAgent → checkInventory("iPhone 15") → "庫存：10 件，有貨"
3. Orchestrator → 回覆管理員："iPhone 15 有貨（10 件），是否為 U001 建立訂單？"
4. 管理員：「是」
5. Orchestrator → askOrderAgent("建立訂單，商品 iPhone 15，數量 1", "U001")
6.   OrderAgent → createOrder("U001", "iPhone 15", 1, idemKey) → "訂單已建立，ID: xxx"
7. Orchestrator → "訂單建立成功，ID: xxx"
```

---

## Sub-Agent @Tool 方法簽章

```java
// InventoryAgent — 只需商品名稱，與使用者身份無關
@Tool("查詢商品庫存數量")
public String ask(
    @ToolParam("要查詢的商品名稱") String productName
)

// OrderAgent — 需要客戶 ID，Java 層組入 user message
@Tool("執行訂單操作（建立、更新、取消、查詢）")
public String ask(
    @ToolParam("訂單任務描述，例如：建立 iPhone 15 訂單數量 1") String task,
    @ToolParam("客戶 ID") String userId
)
```

**設計原則：** 每個 sub-agent 只帶它真正需要的參數，不多傳。

---

## 錯誤處理

- Sub-agent 的 tool 失敗時，回傳 `TRANSIENT_ERROR|...` 或 `BUSINESS_ERROR|...` 字串（現有慣例）
- Sub-agent LLM 將錯誤字串**直接透傳**給 Orchestrator
- Orchestrator system prompt 負責解讀前綴並決定如何回應管理員
- 整個鏈路的錯誤格式一致，不在中間層翻譯

---

## 記憶體設計

| 層級 | Memory |
|---|---|
| Orchestrator | MessageChatMemoryAdvisor + VectorStoreChatMemoryAdvisor（現有） |
| InventoryAgent | 無（stateless） |
| OrderAgent | 無（stateless） |

Sub-agent 每次呼叫是單次任務，context 透過參數傳入，不需要對話歷史。

---

## 新增檔案

| 檔案 | 職責 |
|---|---|
| `client/InventoryServiceClient.java` | HTTP client，GET /api/inventory/{productName}，含 retry + 錯誤轉換 |
| `tool/InventoryAgentTools.java` | `@Tool checkInventory(productName)`，含冪等 key、SSE 事件、Tracing span |
| `agent/InventoryAgent.java` | `@Component`，持有 inventoryAgentChatClient，`@Tool ask(productName)` |
| `agent/OrderAgent.java` | `@Component`，持有 orderAgentChatClient，`@Tool ask(task, userId)` |

## 修改檔案

| 檔案 | 修改內容 |
|---|---|
| `AgentRunExecutor.java` | `.tools(inventoryAgent, orderAgent)` 取代 `.tools(orderAgentTools)` |
| `SpringAiConfig.java` | 新增 sub-agent ChatClient beans；Orchestrator system prompt 改為路由邏輯 |
| `application-dev.yml` | 新增 `inventory-service.base-url: http://localhost:8084` |

---

## System Prompt 設計方向

> 實作時使用 `prompt-engineering` skill 撰寫完整 prompt

**Orchestrator**
- 角色：電商後台管理員助理，負責路由
- 包含：何時呼叫哪個 sub-agent、參數填寫規則、錯誤前綴解讀、管理員操作情境
- Few-shot：查庫存 → 有貨 → 下單 的完整對話範例

**InventoryAgent**
- 角色：庫存查詢專家，只做查詢不做決策
- 包含：checkInventory 工具使用時機、回覆格式（商品名稱、庫存量、是否有貨）
- Few-shot：查到 / 查無此商品 兩種情境

**OrderAgent**
- 角色：訂單管理專家
- 包含：4 個工具的使用時機、從 user message 解析 userId 的規則、錯誤直接透傳規則
- Few-shot：建立 / 查詢 / 取消 訂單

---

## 測試策略

- `InventoryServiceClient`：MockWebServer（okhttp3，已在 test scope）
- `InventoryAgentTools`：Mockito mock InventoryServiceClient
- `InventoryAgent` / `OrderAgent`：mock sub-agent ChatClient，驗證 @Tool 方法正確組裝 user message
- `AgentRunExecutor`：mock inventoryAgent + orderAgent，驗證 tools 綁定正確
