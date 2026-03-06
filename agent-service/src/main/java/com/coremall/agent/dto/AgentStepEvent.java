package com.coremall.agent.dto;

/** Spring ApplicationEvent：通知 SSE 層有 Step 狀態變更。 */
public record AgentStepEvent(
        String runId,
        String toolName,
        String status,   // STARTED / SUCCEEDED / FAILED
        String payload   // result or error message, nullable
) {}
