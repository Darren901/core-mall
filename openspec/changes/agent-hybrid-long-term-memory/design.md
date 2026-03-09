## Context

agent-service 目前使用 `RedisChatMemory`（自訂實作）+ `MessageChatMemoryAdvisor` 做單層短期記憶，視窗固定 20 條。超出視窗的訊息永久遺失。Redis Stack（RedisJSON + RediSearch）已運行，Spring AI 1.1.2 提供原生 `RedisVectorStore` 與 `VectorStoreChatMemoryAdvisor`，可直接整合。

## Goals / Non-Goals

**Goals:**
- 在不影響現有短期記憶行為的前提下，加入長期語意記憶層
- 所有修改集中在 `SpringAiConfig`、`pom.xml`、`application-dev.yml`，不觸碰 Controller / Service / Executor

**Non-Goals:**
- 不實作 Fact Extraction（蒸餾摘要）——留待後續優化
- 不修改 `DELETE /agent/memory/{userId}` 的行為（目前只清 short-term，long-term 不清）
- 不部署新的基礎設施

## Decisions

### D1：雙層 Advisor 順序

`VectorStoreChatMemoryAdvisor`（長期）排在 `MessageChatMemoryAdvisor`（近因）**之前**。

長期層的結果注入 system message，近因層的結果以結構化 USER/ASSISTANT messages 注入。近因層排後可確保最近訊息在 context 中距離 user message 最近，減少 LLM 忽略的機率。

替代方案：近因層在前——被否決，因為 system message 的長期記憶若放在大量 message 物件之後，LLM 較難感知。

### D2：conversationId 過濾

`VectorStoreChatMemoryAdvisor` 使用 `CONVERSATION_ID` advisor param（`userId`）進行 vector search filter，確保只檢索該用戶的歷史，不污染他人記憶。現有 `AgentRunExecutor` 的 `.advisors(a -> a.param(CONVERSATION_ID, userId))` **不需修改**，兩個 advisor 共用同一 param。

### D3：embedding 為同步阻塞

`VectorStoreChatMemoryAdvisor` 在 advisor after 階段同步呼叫 OpenAI Embeddings API 存入向量，增加 ~100-300ms 延遲。接受此 trade-off，換取實作簡單性。後續若延遲不可接受，可包 `@Async` delegate。

### D4：EmbeddingModel 複用現有 OpenAI 設定

不新增 embedding model 設定，Spring AI auto-configuration 自動使用已設定的 OpenAI API key 建立 `EmbeddingModel`（`text-embedding-ada-002`）。

## Risks / Trade-offs

- **[Risk] Embedding API 費用增加** → 每次 run 呼叫一次 embeddings API；電商場景訊息量通常不大，可接受。監控 token 用量。
- **[Risk] Redis Schema 初始化** → `initializeSchema: true` 首次啟動自動建立 RediSearch index，若 Redis Stack 未啟動會拋錯。確保 Docker Compose 先啟動 redis。
- **[Risk] 長期記憶無 TTL** → vector store 內的訊息不設 TTL，資料無限累積。可後續加 TTL 或定期清理策略。
- **[Trade-off] 存入原始訊息而非摘要** → 較 Fact Extraction 噪音多、token 消耗高，但無需額外 LLM 呼叫。

## Migration Plan

1. 更新 `pom.xml` 新增兩個依賴
2. 更新 `application-dev.yml` 新增 vector store 設定
3. 修改 `SpringAiConfig` 新增 `VectorStore` bean 並更新 `ChatClient`
4. 重啟 agent-service，Redis Stack 自動初始化新 index `coremall-ltm`
5. Rollback：移除兩個依賴 + 還原 `SpringAiConfig`，舊的 short-term 記憶不受影響

## Open Questions

~~長期記憶是否應一併被 `DELETE /agent/memory/{userId}` 清除？~~ **已決定**：`AgentMemoryService.clear(userId)` 須同時清除短期記憶（`RedisChatMemory`）與長期記憶（`VectorStore`），確保用戶資料完全抹除。
