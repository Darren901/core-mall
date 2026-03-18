# Core Mall — Agent-Driven Commerce Microservices

高併發電商 AI Agent 微服務練習專案，實作企業級後端工程核心技術：Write-Behind、Outbox Pattern、分散式鎖、冪等性控制，以及由 Spring AI 驅動的 AI Agent（Function Calling + SSE Streaming + Hybrid Memory）。

---

## 架構總覽

```
Browser / Client
      │
      ▼
┌─────────────────────┐
│   gateway-service   │  Spring Cloud Gateway + JWT Filter  :8080
│   (Frontend UI)     │
└──────────┬──────────┘
           │
     ┌─────┴──────┐
     │            │
     ▼            ▼
┌─────────┐  ┌──────────────────────────────────────────┐
│  user-  │  │            order-service  :8082           │
│ service │  │  Write-Behind + Outbox + 分散式鎖 + 冪等  │
│  :8081  │  └──────────────────┬───────────────────────┘
└─────────┘                     │ RabbitMQ (order.events exchange)
                                │ ORDER_CREATED / ORDER_CANCELLED
                                │ ORDER_SAGA_CANCELLED
                    ┌───────────┴────────────┐
                    ▼                        ▼
          ┌──────────────────┐   ┌───────────────────────┐
          │  agent-service   │   │  inventory-service    │
          │     :8083        │   │       :8084           │
          │  Spring AI +     │   │  庫存扣減 / 返庫      │
          │  Gemini + SSE    │   │  Saga 補償消費        │
          └──────────────────┘   └──────────┬────────────┘
                    │                        │ inventory.INSUFFICIENT
                    │                        │ (inventory.events exchange)
          ┌─────────┴─────────┐              │
          ▼                   ▼              ▼
   Redis Stack           PostgreSQL    order-service
  (Chat Memory        (Order DB /      cancelOrderBySaga()
  + Vector Store)      Agent Log)      → SAGA_CANCELLED

┌──────────────────────┐
│  discovery-service   │  Eureka Server  :8761
└──────────────────────┘
```

> 完整 Exchange / Queue / Routing Key 說明見 [docs/eventmap.md](docs/eventmap.md)

---

## 服務說明

| 服務 | 埠號 | 說明 |
|---|---|---|
| `discovery-service` | 8761 | Eureka Server，服務註冊與發現 |
| `gateway-service` | 8080 | Spring Cloud Gateway，JWT 驗證、路由、前端靜態頁面 |
| `user-service` | 8081 | 使用者註冊 / 登入，JWT 簽發 |
| `order-service` | 8082 | 訂單 CRUD，Write-Behind + Outbox Pattern + 分散式鎖 + 冪等性 |
| `agent-service` | 8083 | Multi-Agent Orchestrator，Orchestrator → InventoryAgent / OrderAgent，SSE Streaming + Hybrid Memory |
| `inventory-service` | 8084 | 庫存扣減 / 返庫，消費 order.events；Saga 補償事件發佈至 inventory.events |

---

## 核心技術實作

### Write-Behind Pattern（order-service）
- Redis 作為寫入緩衝層，命令接受即回應 201，非同步寫入 PostgreSQL
- 分散式鎖（Redisson）防止並發寫入衝突
- 冪等性：`idem:{idempotency-key}` 存 Redis，防止重複提交

### Outbox Pattern（order-service）
- 訂單操作與 Outbox 事件在同一 DB Transaction 寫入
- `OutboxRelayService` 定期輪詢發佈至 RabbitMQ
- 消費端冪等：`messageId` 存 `processed_events` table

### Saga Pattern（order-service ↔ inventory-service）
- 下單觸發庫存扣減；庫存不足時發補償事件，自動取消訂單
- 區分 `CANCELLED`（使用者取消，需返庫）與 `SAGA_CANCELLED`（補償取消，不返庫）
- 完整流程見 [docs/eventmap.md](docs/eventmap.md)

### AI Agent（agent-service）
- **Spring AI 1.1.2** + **Gemini 2.5 Flash**（Orchestrator）/ **Claude Haiku 4.5**（可切換）
- **Multi-Agent Orchestrator 架構**：
  ```
  Orchestrator LLM
       ├── askInventoryAgent → InventoryAgent → checkInventory → inventory-service
       └── askOrderAgent     → OrderAgent     → createOrder / updateOrder / cancelOrder / getOrderStatus → order-service
  ```
  - Orchestrator 負責路由與對話記憶，不直接呼叫 Tool
  - 子代理（InventoryAgent / OrderAgent）各自持有 stateless ChatClient，只負責單一領域
  - 商品名稱標準化規則嵌入 Orchestrator system prompt（i15 → iPhone 15）
- **SSE Streaming**：每個 Tool Call 步驟即時推送前端，事件帶 `agentName` + `toolName` + `status`
- **Hybrid Memory**（Orchestrator 層）：
  - 短期：`MessageChatMemoryAdvisor`（Redis，最近 20 輪）
  - 長期：`VectorStoreChatMemoryAdvisor`（Redis Stack Vector Store，語意相似度檢索）
  - Embedding：`gemini-embedding-001`（3072 維，HNSW + Cosine）

### Agent 穩定性
- **Tool 層 Retry**：`TransientAiException` 自動重試 3 次（exponential backoff），`NonTransientAiException` 不重試
- **錯誤前綴**：Tool 回傳值加 `TRANSIENT_ERROR|` / `BUSINESS_ERROR|` 前綴，讓 LLM 能分類處理錯誤
- **冪等性（Tool 層）**：`step:{runId}:{toolName}:{params}` 存 Redis（TTL 1 小時），防止 LLM 重複呼叫同一 Tool
- **友善錯誤訊息**：LLM API 授權失敗、rate limit、服務不可用各有對應的中文回覆

### 可觀測性（Observability）
- **Micrometer Tracing + Brave**：所有服務加 traceId / spanId，log 自動附帶
- **Zipkin**：分散式追蹤，`http://localhost:9411`
- **Agent 專屬 Span**：每個 AgentRun 產生 root span，Tool Call 為 child span，可在 Zipkin 看完整呼叫鏈

---

## 快速啟動

### 環境需求
- Java 21
- Maven 3.9+
- Docker & Docker Compose

### 1. 啟動基礎設施

```bash
docker compose -f infra/docker-compose.yml up -d
```

啟動服務：
- PostgreSQL × 3（port 5432 / 5433 / 5434）
- Redis Stack（port 6379，RedisInsight 8001）
- RabbitMQ（port 5672，Management UI 15672）
- Zipkin（port 9411）

### 2. 設定 API Key

在 `agent-service/src/main/resources/` 建立 `application-sensitive.yml`：

```yaml
spring:
  ai:
    google:
      genai:
        api-key: YOUR_GOOGLE_GENAI_API_KEY
```

> Google AI Studio 申請：https://aistudio.google.com/apikey

### 3. 啟動服務（依序）

```bash
# 各服務目錄下執行
mvn spring-boot:run -pl discovery-service
mvn spring-boot:run -pl gateway-service
mvn spring-boot:run -pl user-service
mvn spring-boot:run -pl order-service
mvn spring-boot:run -pl agent-service
mvn spring-boot:run -pl inventory-service
```

### 4. 開啟前端

瀏覽器開啟 [http://localhost:8080](http://localhost:8080)，註冊帳號後即可與 AI Agent 對話。

---

## 開發指令

```bash
mvn clean compile              # 編譯
mvn test                       # 執行測試
mvn clean verify               # 含 JaCoCo 覆蓋率（Line ≥ 80%、Branch ≥ 80%）
```

---

## 壓測（k6）

### 安裝

```bash
brew install k6
```

### 場景

| 腳本 | 驗證目標 |
|---|---|
| `k6/order-concurrent.js` | 冪等性（同 key 打 10 次）+ 分散式鎖（50 VU 並發） |
| `k6/inventory-oversell.js` | 庫存超賣防護 + Saga 最終一致性（100 VU 搶 5 個庫存） |
| `k6/agent-sse.js` | Agent SSE 全流程（需 loadtest profile，不打真實 LLM） |

```bash
k6 run k6/order-concurrent.js
k6 run -e SAGA_WAIT=15 k6/inventory-oversell.js

# agent-sse 需先以 loadtest profile 啟動 agent-service
mvn spring-boot:run -pl agent-service -Dspring-boot.run.profiles=loadtest
k6 run k6/agent-sse.js
```

> loadtest profile 使用 `MockChatModel` 替換真實 LLM，不消耗 API quota。詳見 `k6/README.md`。

---

## Tech Stack

| 分類 | 技術 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.12、Spring Cloud 2023.0.5 |
| AI | Spring AI 1.1.2、Google Gemini 2.5 Flash、gemini-embedding-001 |
| Database | PostgreSQL 16 |
| Cache / Vector DB | Redis Stack（RediSearch + RedisJSON） |
| Message Broker | RabbitMQ 3.13 |
| Test | JUnit 5、Mockito、Testcontainers 1.21.3 |
| Load Test | k6 |
| Observability | Micrometer Tracing、Brave、Zipkin 3 |
| Build | Maven 3.9 |
