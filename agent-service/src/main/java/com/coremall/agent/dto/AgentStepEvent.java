package com.coremall.agent.dto;

/** Spring ApplicationEvent：通知 SSE 層有 Step 狀態變更。 */
public record AgentStepEvent(
        String runId,
        String agentName,   // 執行此 tool 的子代理名稱，例如 InventoryAgent / OrderAgent
        String toolName,
        String status,      // STARTED / SUCCEEDED / FAILED
        String payload      // result or error message, nullable
) {}
