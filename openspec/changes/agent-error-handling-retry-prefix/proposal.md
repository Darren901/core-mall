## Why

agent-service 的 OrderServiceClient 對所有 HTTP 錯誤只做一次性捕獲，沒有重試機制，導致瞬間 5xx 或網路抖動即造成 Tool 失敗；且 Tool 回傳的錯誤字串無類型前綴，LLM 無法區分「系統暫時不可用」與「業務規則拒絕」，無法給出適當的使用者引導。

## What Changes

- **shared-kernel** 新增 `ServiceTransientException`（5xx / IOException）與 `ServiceBusinessException`（4xx）
- **OrderServiceClient.toFriendlyError()** 依 HTTP status 拋出對應例外類型
- **OrderServiceClient** 各方法加 `.retryWhen()` — 對 `ServiceTransientException` / `IOException` 做 exponential backoff retry（最多 3 次，200ms 起始，2s 上限，jitter 0.5）
- **OrderAgentTools** 各 catch 區塊依例外類型加錯誤前綴：`TRANSIENT_ERROR|` 或 `BUSINESS_ERROR|`
- **SpringAiConfig System Prompt** 新增錯誤前綴處理規則與對應範例

## Capabilities

### New Capabilities

- `order-client-retry`: OrderServiceClient 對暫時性錯誤的 retry 機制（backoff + filter）
- `agent-tool-error-prefix`: Tool 層錯誤前綴分類（TRANSIENT_ERROR / BUSINESS_ERROR）與 LLM 處理規則

### Modified Capabilities

（無 spec-level 行為變更）

## Impact

- `shared-kernel`：新增 exception package，所有服務均可引用
- `agent-service`：OrderServiceClient、OrderAgentTools、SpringAiConfig 三個檔案修改
- 4xx 錯誤不再觸發 retry，行為與現在一致
- 5xx / IOException 最多延遲約 4.4s（3次 retry + backoff）後才回傳錯誤
