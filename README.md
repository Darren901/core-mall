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
┌─────────┐  ┌─────────────┐
│  user-  │  │   order-    │
│ service │  │   service   │  Write-Behind + Outbox + 分散式鎖
│  :8081  │  │   :8082     │
└─────────┘  └──────┬──────┘
                    │ RabbitMQ
                    ▼
          ┌──────────────────┐
          │  agent-service   │  Spring AI + Gemini + Function Calling
          │     :8083        │  SSE Streaming + Hybrid Memory
          └──────────────────┘
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
   Redis Stack           PostgreSQL
  (Chat Memory        (Agent Run Log
  + Vector Store)      + Order DB)

┌──────────────────────┐
│  discovery-service   │  Eureka Server  :8761
└──────────────────────┘
```

---

## 服務說明

| 服務 | 埠號 | 說明 |
|---|---|---|
| `discovery-service` | 8761 | Eureka Server，服務註冊與發現 |
| `gateway-service` | 8080 | Spring Cloud Gateway，JWT 驗證、路由、前端靜態頁面 |
| `user-service` | 8081 | 使用者註冊 / 登入，JWT 簽發 |
| `order-service` | 8082 | 訂單 CRUD，Write-Behind + Outbox Pattern + 分散式鎖 + 冪等性 |
| `agent-service` | 8083 | AI Agent，Function Calling + SSE Streaming + Hybrid Long-Term Memory |

---

## 核心技術實作

### Write-Behind Pattern（order-service）
- Redis 作為寫入緩衝層，命令接受即回應 201，非同步寫入 PostgreSQL
- 分散式鎖（Redisson）防止並發寫入衝突
- 冪等性：`idem:{idempotency-key}` 存 Redis，防止重複提交

### Outbox Pattern（order-service）
- 訂單操作與 Outbox 事件在同一 DB Transaction 寫入
- `OutboxRelayService` 定期輪詢發佈至 RabbitMQ
- 消費端冪等：`step:{runId}:{toolName}:{params}` TTL 1 小時

### AI Agent（agent-service）
- **Spring AI 1.1.2** + **Gemini 2.5 Flash**
- Function Calling：createOrder、updateOrder、cancelOrder、getOrderStatus
- SSE Streaming：每個 Tool Call 步驟即時推送前端
- **Hybrid Memory**：
  - 短期：`MessageChatMemoryAdvisor`（Redis，最近 20 輪）
  - 長期：`VectorStoreChatMemoryAdvisor`（Redis Stack Vector Store，語意相似度檢索）
  - Embedding：`gemini-embedding-001`（3072 維，HNSW + Cosine）

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
| Build | Maven 3.9 |
