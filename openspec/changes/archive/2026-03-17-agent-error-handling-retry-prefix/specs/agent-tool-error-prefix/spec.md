## ADDED Requirements

### Requirement: Tool 錯誤回傳加類型前綴
OrderAgentTools 的所有 Tool 方法在捕獲例外後，SHALL 依例外類型在回傳字串前加上前綴：
- `ServiceTransientException`（含 retry 耗盡後的包裝）→ `TRANSIENT_ERROR|{message}`
- 其他例外 → `BUSINESS_ERROR|{message}`

#### Scenario: 5xx retry 耗盡回傳 TRANSIENT_ERROR 前綴
- **WHEN** OrderServiceClient retry 耗盡後拋出例外，根因為 ServiceTransientException
- **THEN** Tool 回傳 `TRANSIENT_ERROR|{訊息}`

#### Scenario: 4xx 回傳 BUSINESS_ERROR 前綴
- **WHEN** OrderServiceClient 拋出 ServiceBusinessException
- **THEN** Tool 回傳 `BUSINESS_ERROR|{訊息}`

#### Scenario: 非預期例外回傳 BUSINESS_ERROR 前綴
- **WHEN** Tool 方法捕獲到非 ServiceTransientException 的例外
- **THEN** Tool 回傳 `BUSINESS_ERROR|{訊息}`

### Requirement: LLM 依錯誤前綴決定回覆方式
System Prompt SHALL 包含錯誤前綴處理規則，讓 LLM 能區分兩種錯誤類型並給出適當回覆。

#### Scenario: LLM 收到 TRANSIENT_ERROR 告知使用者稍後再試
- **WHEN** Tool 回傳以 `TRANSIENT_ERROR|` 開頭的字串
- **THEN** LLM 告知使用者服務暫時不可用，建議稍後再試，不再重試 Tool

#### Scenario: LLM 收到 BUSINESS_ERROR 說明業務原因
- **WHEN** Tool 回傳以 `BUSINESS_ERROR|` 開頭的字串
- **THEN** LLM 直接說明 `|` 後的錯誤原因，並提供具體建議
