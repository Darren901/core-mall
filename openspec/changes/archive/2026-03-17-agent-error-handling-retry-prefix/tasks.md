## 1. shared-kernel 例外類別

- [x] 1.1 新增 `ServiceTransientException`（`com.coremall.sharedkernel.exception`，繼承 RuntimeException）
- [x] 1.2 新增 `ServiceBusinessException`（`com.coremall.sharedkernel.exception`，繼承 RuntimeException）
- [x] 1.3 為 1.1 / 1.2 補齊單元測試

## 2. OrderServiceClient 錯誤類型區分

- [x] 2.1 修改 `toFriendlyError()`：5xx → `ServiceTransientException`，4xx → `ServiceBusinessException`
- [x] 2.2 新增 `retryPolicy` field（backoff 3, 200ms, maxBackoff 2s, jitter 0.5，filter: ServiceTransientException | IOException）；改為 field 注入以支援測試替換 policy
- [x] 2.3 在 `createOrder`、`updateOrder`、`cancelOrder`、`getOrder` 各加 `.retryWhen(retryPolicy)`
- [x] 2.4 補齊 OrderServiceClient 測試（5xx retry 成功、retry 耗盡、4xx 不 retry、createOrder retry 冪等）

## 3. OrderAgentTools 錯誤前綴

- [x] 3.1 修改 4 個 Tool 方法的 catch 區塊：依例外根因加 `TRANSIENT_ERROR|` 或 `BUSINESS_ERROR|` 前綴
- [x] 3.2 補齊 OrderAgentTools 測試（TRANSIENT_ERROR 前綴、BUSINESS_ERROR 前綴、非預期 RuntimeException → BUSINESS_ERROR）

## 4. System Prompt 更新

- [x] 4.1 在 `SpringAiConfig.SYSTEM_PROMPT` 新增「工具錯誤分類處理」段落
- [x] 4.2 新增範例 E（TRANSIENT_ERROR）、範例 F（BUSINESS_ERROR）、範例 G（資訊模糊確認）

## 5. 驗證

- [x] 5.1 執行單元測試（shared-kernel + agent-service），94 tests pass，6.3 秒完成
