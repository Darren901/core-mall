## Context

agent-service 的 `OrderServiceClient` 使用 WebClient（reactor）呼叫 order-service，透過 `.block()` 轉為同步。目前 `toFriendlyError()` 對所有 4xx / 5xx 統一拋 `RuntimeException`，`OrderAgentTools` 的 catch 區塊直接將訊息字串回傳給 LLM，LLM 無法判斷是否應引導使用者稍後重試。

現有冪等保護：所有寫入操作攜帶 `X-Idempotency-Key`（格式：`{runId}-{toolName}-{resourceId}`），order-service 端有冪等防護，retry 安全。

## Goals / Non-Goals

**Goals:**
- 5xx / IOException / timeout 自動 retry，最多 3 次，exponential backoff
- 4xx 不 retry（業務規則拒絕，重試無意義）
- Tool 回傳值加前綴，LLM 可判斷錯誤類型

**Non-Goals:**
- AgentRunExecutor 層（TransientAiException）的 retry
- Circuit Breaker / Resilience4j
- retry 次數可設定化

## Decisions

### D1：例外類型放 shared-kernel，不放 agent-service

**選擇**：`com.coremall.sharedkernel.exception` 新增 `ServiceTransientException` / `ServiceBusinessException`。

**理由**：這兩個例外語意屬於「呼叫下游服務的結果分類」，若其他服務（如 inventory-service）未來也有同類 client，可直接複用，不需重複定義。

**替代方案**：放在 `agent-service/client/`。缺點是若其他服務需要相同語意，需重複定義。

---

### D2：retry 放在 Mono 鏈 `.retryWhen()`，不放 WebClient Filter

**選擇**：在各 Client 方法的 `.block()` 前加 `.retryWhen(retrySpec())`。

**理由**：WebClient Filter 在 `toFriendlyError()` 吃掉 HTTP status 之前執行，難以在 filter 層區分 4xx vs 5xx；Mono 鏈 `.retryWhen()` 作用在 exception 類型上，分類已清楚。`retrySpec()` 抽成共用 private method，各方法一行加入，不重複。

**替代方案**：WebClient Filter 全域 retry。缺點：filter 執行時尚未轉換例外，需在 filter 內重讀 status code，複雜度更高。

---

### D3：retry spec 參數

```
backoff(3, 200ms) — 最多 3 次
maxBackoff: 2s
jitter: 0.5
filter: ServiceTransientException | IOException
```

最壞情境延遲估算：200ms + 400ms + 800ms ≈ 1.4s（不含 jitter）。加上首次失敗，Tool 最多約 4–5s 後回傳。Agent SSE timeout 為 5 分鐘，不會觸發。

---

### D4：錯誤前綴格式 `TRANSIENT_ERROR|` / `BUSINESS_ERROR|`

**選擇**：`{PREFIX}|{human-readable message}`，`|` 作為分隔符。

**理由**：LLM 可用前綴做條件判斷，`|` 後的內容直接可讀，System Prompt 範例可清楚示範。前綴全大寫 + `_` 風格與工程命名一致，不易被 LLM 誤認為一般文字。

## Risks / Trade-offs

- **延遲增加**：5xx 錯誤最多增加 ~4s 延遲。可接受，相對於「直接失敗」體驗更好。
- **RetryExhaustedException 包裝**：Reactor retry 耗盡後拋 `RetryExhaustedException`，其 `getCause()` 才是原始例外。Tool catch 需用 `ExceptionUtils` 或 `e.getCause()` 取原始類型。→ 以 `instanceof` 先判斷 `RetryExhaustedException`，取 cause 再判斷前綴。
- **idempotency-key 碰撞**（理論）：同一 runId 同一 tool 呼叫相同參數 retry，key 一致，order-service 正確防重。無風險。

## Migration Plan

1. 新增 shared-kernel 例外類別（無 breaking change）
2. 修改 OrderServiceClient（行為：5xx 由一次失敗改為最多 4 次嘗試）
3. 修改 OrderAgentTools 錯誤字串格式（**Breaking**：LLM 看到的 Tool 回傳格式改變，需同步更新 System Prompt）
4. 更新 System Prompt（步驟 3 同步完成）

Rollback：還原 3 個 agent-service 檔案即可，shared-kernel 新增類別不影響其他服務。
