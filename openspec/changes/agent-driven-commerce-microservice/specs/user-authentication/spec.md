## ADDED Requirements

### Requirement: 用戶註冊
系統必須允許新用戶以 email 和密碼進行註冊。

#### Scenario: 註冊成功
- **WHEN** 客戶端傳送有效的 email 和密碼至 `POST /api/v1/auth/register`
- **THEN** 系統應以 BCrypt 雜湊密碼建立用戶，並回傳 HTTP 201

#### Scenario: 重複 email 被拒絕
- **WHEN** 客戶端以已存在的 email 進行註冊
- **THEN** 系統應回傳 HTTP 409 Conflict

#### Scenario: 無效輸入被拒絕
- **WHEN** 客戶端傳送空白 email 或長度不足最小值的密碼
- **THEN** 系統應回傳 HTTP 400 並附上驗證錯誤詳情

---

### Requirement: 用戶登入與 JWT 簽發
系統必須驗證用戶身份，並在登入成功後簽發 JWT。

#### Scenario: 登入成功
- **WHEN** 客戶端傳送有效憑證至 `POST /api/v1/auth/login`
- **THEN** 系統應回傳 HTTP 200 及包含 `userId` 和 `email` claims 的已簽名 JWT

#### Scenario: 密碼錯誤被拒絕
- **WHEN** 客戶端傳送正確 email 但錯誤密碼
- **THEN** 系統應回傳 HTTP 401 Unauthorized

#### Scenario: 不存在的用戶被拒絕
- **WHEN** 客戶端傳送不存在的 email
- **THEN** 系統應回傳 HTTP 401 Unauthorized

---

### Requirement: Gateway JWT 驗證與用戶身份傳遞
API Gateway 必須對每個受保護的請求驗證 JWT，並將用戶身份傳遞給下游服務。

#### Scenario: 有效 token 通過驗證
- **WHEN** 客戶端在 Authorization header 帶有效 JWT 發送請求
- **THEN** Gateway 應注入 `X-User-Id` header 後轉發請求，並移除原始 Authorization header

#### Scenario: 無效或過期 token 在 Gateway 被拒絕
- **WHEN** 客戶端傳送無效或已過期的 JWT
- **THEN** Gateway 應在轉發至任何下游服務前回傳 HTTP 401

#### Scenario: 下游服務讀取用戶身份
- **WHEN** 下游服務（agent-service、order-service）收到請求
- **THEN** 應從 `X-User-Id` header 讀取用戶身份，不重複驗證 JWT
