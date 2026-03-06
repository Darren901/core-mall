# Core-Mall 高併發模式學習心得

## 1. Write-Behind（寫後延遲）模式

### 設計
```
寫入請求 → Redis → 立即返回
              ↓ (@Scheduled, 每 5 秒)
         OutboxRelayService → PostgreSQL + outbox_event（同一 transaction）
```

### 核心學習
- **Redis 是暫時的 Source of Truth**：在 relay 完成前，資料只在 Redis。Redis 有 AOF 持久化，所以不算「不可靠」。
- **DB 是持久備份**，不是「確認點」。HTTP 201 回傳表示「已接受」，不等於「已入庫」。
- **為什麼這樣設計？** 高吞吐量場景下，每次寫入都等 DB 是效能瓶頸。Redis 的寫入速度是 PostgreSQL 的 10-100 倍。

### 實際案例
Amazon、Shopee 的購物車系統，下單後說「訂單已提交」，不是說「已寫入 DB」。

---

## 2. Outbox Pattern（寄件匣模式）

### 設計
```
OrderCommandService
  ├── 寫 Redis（訂單資料）
  └── （後台）OutboxRelayService
          ├── 讀 Redis → 寫 PostgreSQL（訂單）
          └── 寫 outbox_events（同一 transaction）← 關鍵
                      ↓
              OutboxPublisher (@Scheduled)
                      ↓
              RabbitMQ exchange（order.events）
```

### 核心學習
- **原子性問題**：如果「寫 DB」和「發 MQ」不在同一個 transaction，中間崩潰就會有一個成功一個失敗。
- **解法**：先寫 outbox_events（同 DB transaction），再由 Scheduler 讀取並 publish 到 MQ。就算 Scheduler 崩潰，下次重啟還能繼續 publish。
- **MQ 的目的**：通知下游消費者（庫存、通知、報表），不是用來「確認命令成功」。

### 關鍵洞察
> Outbox + MQ 是給「下游消費者」用的，不是給「發命令的人」用來確認寫入。

---

## 3. 分散式鎖（Distributed Lock）

### 設計
```java
// Redis SETNX + TTL
Boolean locked = redisTemplate.opsForValue()
    .setIfAbsent("lock:order:" + orderId, "1", 5, TimeUnit.SECONDS);
```

### 核心學習
- **SETNX（SET if Not eXists）**：原子性，只有第一個請求能設置成功。
- **TTL 很重要**：如果持有鎖的服務崩潰，鎖會自動釋放，不會死鎖。
- **適用場景**：update / cancel 這種需要防止並發修改同一筆資料的操作。
- **不適用場景**：create（不同使用者可以並發建立不同訂單）。

---

## 4. 冪等性（Idempotency）

### 兩層冪等設計

**Layer 1：HTTP 層（order-service）**
```
Redis key: idem:{idempotency-key}
→ 相同 key 再次請求 → 返回第一次的結果
```

**Layer 2：Tool Call 層（agent-service）**
```
Redis key: step:{runId}:{toolName}:{params}
→ LLM 重試同一個 tool call → 直接返回快取結果
```

### 核心學習
- **為什麼要兩層？** LLM 可能因為 timeout 或 hallucination 重試 tool call。如果 tool 本身不冪等，就會重複建立訂單。
- **Key 的設計很重要**：太粗（只用 toolName）→ 不同參數的請求被誤認為重複。太細（包含 timestamp）→ 完全沒有防護效果。
- **TTL 設定**：設 1 小時。同一次 Agent session 內重試有保護；跨天的新請求不受影響。

---

## 5. Outbox Consumer 冪等性

### 設計
```java
// 消費 MQ 時先查 processed_events
if (processedEventRepository.existsById(messageId)) {
    return; // 已處理過，跳過
}
processedEventRepository.save(ProcessedEvent.of(messageId));
```

### 核心學習
- **MQ 不保證 Exactly-Once**：就算 Publisher 確認 ack，網路問題可能導致重送。
- **At-Least-Once + Consumer 冪等 = Effectively Exactly-Once**：消費端自己保證不重複處理。
- **messageId 是關鍵**：Publisher 設定 `message.getMessageProperties().setMessageId(eventId)`，Consumer 用這個去重。

---

## 6. Spring AI Function Calling + SSE Streaming

### 設計
```
POST /agent/chat → startRun() → 建立 AgentRun + SSE Sink
                             → @Async executeAsync()
                                   → ChatClient.prompt().tools(orderAgentTools).call()
                                         → LLM 決定 call tool
                                               → @Tool 方法執行
                                                     → 發 ApplicationEventPublisher
                                                     → AgentRunService.onStepEvent()
                                                     → sink.tryEmitNext(SSE event)
GET /sessions/{id}/stream → sink.asFlux() → SSE 推給 Client
```

### 核心學習

**Sinks.many().replay().limit(100)** vs **multicast()**
- `multicast`：只有訂閱後才收到事件，晚訂閱的客戶端會漏掉已發出的事件。
- `replay().limit(100)`：最多快取 100 個事件，晚訂閱的客戶端仍可補收。→ SSE 客戶端連接可能比事件發出晚幾秒，用 replay 才可靠。

**ThreadLocal + @Async 的陷阱**
- `@Async` 在新執行緒執行，ThreadLocal 不自動傳遞。
- `AgentRunContext.set(runId)` 必須在 `executeAsync()` 開頭設定，在 `finally` 清除。
- Unit Test 中 `@Async` 沒有 Spring Proxy，方法同步執行 → 設計測試時要注意這個行為。

**@ToolParam 與 @Tool 的分工**
- `@Tool(description)` → 描述整個 tool 的「用途」，LLM 用來決定「要不要呼叫這個 tool」。
- `@ToolParam(description)` → 描述每個參數的「意義與約束」，LLM 用來決定「傳什麼值」。

---

## 7. 架構回顧：Agent 為什麼不需要消費 MQ？

**誤解**：「order-service 用 write-behind，所以 agent 要等 MQ 確認才算成功」

**正確理解**：
- `OrderAgentTools.createOrder()` 呼叫 order-service HTTP，收到 201 + orderId。
- 此時資料已在 Redis（有 AOF 持久化保護），orderId 是有效的。
- 後續的 `updateOrder("order-xxx")` 也走 write-behind，也讀 Redis → 都是一致的。
- MQ 事件是給「其他服務」（庫存、通知）用的，不是給 agent 自己用的。

**什麼情況 Agent 才需要消費 MQ？**
- 當 agent 需要感知「付款確認」、「物流狀態變更」等非 agent 觸發的事件。
- 也就是說：Agent 自己發的命令，用 HTTP ACK；其他服務的狀態變更，才用 MQ。

---

## 8. 微服務拆分原則（從這個專案學到的）

| 服務 | 職責 | 技術特點 |
|---|---|---|
| user-service | 身份認證（JWT 簽發） | BCrypt + JWT + PostgreSQL |
| order-service | 訂單 CRUD（高吞吐量） | Write-behind + 分散式鎖 + 冪等 + Outbox |
| agent-service | AI 驅動的自然語言操作 | LLM + Function Calling + SSE |
| gateway-service | 統一入口 + JWT 驗證 | Spring Cloud Gateway + Filter |
| discovery-service | 服務發現與負載均衡 | Eureka Server |

**關鍵設計決策**：
- 各服務有獨立 DB（PostgreSQL × 3）→ 避免跨 DB 事務，強制透過 API 通信。
- Shared Redis → 可以跨服務共用快取（未來可拆）。
- agent-service 透過 WebClient 呼叫 order-service → 同步 HTTP 而非 MQ，因為需要 tool 結果。
