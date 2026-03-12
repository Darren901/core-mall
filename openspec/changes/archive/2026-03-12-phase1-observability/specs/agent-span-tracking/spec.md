## ADDED Requirements

### Requirement: AgentRun 建立 root span
`AgentRunExecutor.execute()` SHALL 為每次 Agent Run 建立名為 `agent.run` 的 span，並帶上 `runId` 與 `userId` tag。

#### Scenario: Agent Run 執行成功時 span 完整記錄
- **WHEN** `AgentRunExecutor.execute()` 被呼叫
- **THEN** 建立名為 `agent.run` 的 span
- **THEN** span 帶有 `runId` tag 等於傳入的 runId
- **THEN** span 帶有 `userId` tag 等於傳入的 userId
- **THEN** span 在 execute() 結束時關閉（end() 被呼叫）

#### Scenario: Agent Run 執行失敗時 span 仍關閉
- **WHEN** `AgentRunExecutor.execute()` 拋出例外
- **THEN** span 仍然被關閉（不洩漏 span）

### Requirement: Tool Call 建立 child span
`OrderAgentTools` 的每個 `@Tool` 方法 SHALL 建立名為 `agent.tool.{toolName}` 的 child span，並帶上 `runId` tag。

#### Scenario: createOrder tool call 建立 child span
- **WHEN** `OrderAgentTools.createOrder()` 被呼叫
- **THEN** 建立名為 `agent.tool.createOrder` 的 span
- **THEN** span 帶有 `runId` tag

#### Scenario: tool call 失敗時 span 仍關閉
- **WHEN** `@Tool` 方法內部發生例外
- **THEN** span 仍然被關閉（不洩漏 span）

### Requirement: Span 階層正確
AgentRun span 與 Tool Call span SHALL 形成正確的父子關係，在 Zipkin 呈現為：`agent.run → agent.tool.*`。

#### Scenario: Zipkin 顯示 span 階層
- **WHEN** 一次 Agent Run 觸發一個 tool call
- **THEN** `agent.tool.*` span 的 parentSpanId 等於 `agent.run` span 的 spanId
