## 1. shared-kernel 例外類別

- [ ] 1.1 新增 `ServiceTransientException`（`com.coremall.sharedkernel.exception`，繼承 RuntimeException）
- [ ] 1.2 新增 `ServiceBusinessException`（`com.coremall.sharedkernel.exception`，繼承 RuntimeException）
- [ ] 1.3 為 1.1 / 1.2 補齊單元測試

## 2. OrderServiceClient 錯誤類型區分

- [ ] 2.1 修改 `toFriendlyError()`：5xx → `ServiceTransientException`，4xx → `ServiceBusinessException`
- [ ] 2.2 新增 private `retrySpec()` 方法（backoff 3, 200ms, maxBackoff 2s, jitter 0.5，filter: ServiceTransientException | IOException）
- [ ] 2.3 在 `createOrder`、`updateOrder`、`cancelOrder`、`getOrder` 各加 `.retryWhen(retrySpec())`
- [ ] 2.4 補齊 OrderServiceClient 測試（5xx retry 成功、retry 耗盡、4xx 不 retry、IOException retry）

## 3. OrderAgentTools 錯誤前綴

- [ ] 3.1 修改 4 個 Tool 方法的 catch 區塊：依例外根因加 `TRANSIENT_ERROR|` 或 `BUSINESS_ERROR|` 前綴
- [ ] 3.2 補齊 OrderAgentTools 測試（TRANSIENT_ERROR 前綴、BUSINESS_ERROR 前綴）

## 4. System Prompt 更新

- [ ] 4.1 在 `SpringAiConfig.SYSTEM_PROMPT` 新增「工具錯誤分類處理」段落
- [ ] 4.2 新增範例 F（TRANSIENT_ERROR 處理）與範例 G（BUSINESS_ERROR 處理）

## 5. 驗證

- [ ] 5.1 執行 `mvn clean verify`（shared-kernel + agent-service），確認 JaCoCo 覆蓋率 ≥ 80%
