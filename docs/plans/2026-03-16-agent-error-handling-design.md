# Agent 錯誤處理優化設計

**日期**：2026-03-16
**範圍**：agent-service、shared-kernel
**目標**：A) Client 層 retry B) Tool 層錯誤前綴供 LLM 判斷

---

## 背景

目前 agent-service 整條鏈路（Tool → Client → Executor）完全沒有重試機制。所有錯誤一次性捕獲後轉為字串回傳給 LLM，LLM 無法區分「系統暫時不可用（可等待）」與「業務規則拒絕（不應重試）」。

---

## 設計方案

### A：OrderServiceClient Retry

#### 自訂例外（shared-kernel）

新增 package `com.coremall.sharedkernel.exception`：

| 類別 | 觸發條件 | 語意 |
|---|---|---|
| `ServiceTransientException` | 5xx response | 系統暫時不可用，可重試 |
| `ServiceBusinessException` | 4xx response | 業務規則拒絕，不重試 |

#### toFriendlyError() 修改

```java
// 改前：所有錯誤拋 RuntimeException
return new RuntimeException("服務暫時無法處理請求，請稍後再試");

// 改後：依 HTTP status 區分
if (resp.statusCode().is5xxServerError()) {
    return new ServiceTransientException(message);
} else {
    return new ServiceBusinessException(message);
}
```

#### Retry Spec

```java
private Retry retrySpec() {
    return Retry.backoff(3, Duration.ofMillis(200))
            .maxBackoff(Duration.ofSeconds(2))
            .jitter(0.5)
            .filter(e -> e instanceof ServiceTransientException
                      || e instanceof IOException);
}
```

- **重試次數**：最多 3 次（加上首次共 4 次嘗試）
- **退避起始**：200ms
- **最大退避**：2s
- **Jitter**：0.5（避免 thundering herd）
- **觸發條件**：`ServiceTransientException`（5xx）或 `IOException`（網路/timeout）
- **不觸發**：`ServiceBusinessException`（4xx）

#### 每個方法加 `.retryWhen()`

```java
.bodyToMono(new ParameterizedTypeReference<ApiResponse<OrderResult>>() {})
.map(ApiResponse::data)
.retryWhen(retrySpec())   // ← 加在 block() 前
.block();
```

Reactor re-subscribe 語意：retry 時整個 HTTP 請求（含 header、body）重新發送。

#### 冪等性確認

所有寫入操作已攜帶 `X-Idempotency-Key`，格式 `{runId}-{toolName}-{resourceId}`。order-service 端有冪等保護，retry 不會重複建立/更新/取消。✅

---

### B：Tool 層錯誤前綴

#### OrderAgentTools catch 區塊修改

```java
// 改前
String error = "訂單建立失敗：" + e.getMessage();

// 改後
String prefix = (e instanceof ServiceTransientException) ? "TRANSIENT_ERROR" : "BUSINESS_ERROR";
String error = prefix + "|" + e.getMessage();
```

4 個 Tool 方法（createOrder / updateOrder / cancelOrder / getOrderStatus）全部套用相同模式。

#### System Prompt 新增段落

在「核心行為規則」區塊新增：

```
### 工具錯誤分類處理
工具回傳值可能含有錯誤前綴，必須依前綴決定回覆方式：
- TRANSIENT_ERROR|...：系統暫時不可用（已自動重試 3 次後仍失敗）
  → 告知使用者服務暫時無法使用，請稍後再試，不要再重試工具
- BUSINESS_ERROR|...：業務規則拒絕，不可重試
  → 直接說明 | 後的錯誤原因，並提供具體建議
```

新增範例 F（TRANSIENT_ERROR）與範例 G（BUSINESS_ERROR）。

---

## 涉及檔案

| 檔案 | 變更類型 |
|---|---|
| `shared-kernel/.../exception/ServiceTransientException.java` | 新增 |
| `shared-kernel/.../exception/ServiceBusinessException.java` | 新增 |
| `agent-service/.../client/OrderServiceClient.java` | 修改 |
| `agent-service/.../tool/OrderAgentTools.java` | 修改 |
| `agent-service/.../config/SpringAiConfig.java` | 修改（System Prompt） |

---

## 不在範圍內

- AgentRunExecutor 層的 retry（TransientAiException）：風險較高，需另外評估
- Circuit Breaker / Resilience4j：過度設計，目前不引入
- WebClient 全域 filter retry：改為 per-method 以便精細控制

---

## 測試計畫

| 測試 | 驗證點 |
|---|---|
| 5xx retry 成功（第 2 次成功） | 最終回傳正確結果，無例外 |
| 5xx retry 耗盡（3 次都失敗） | Tool 回傳 `TRANSIENT_ERROR|...` |
| 4xx 不 retry | 第 1 次失敗直接回傳 `BUSINESS_ERROR|...` |
| IOException retry | 網路錯誤觸發 retry |
| createOrder retry 冪等性 | 相同 idempotencyKey 不重複建立 |
