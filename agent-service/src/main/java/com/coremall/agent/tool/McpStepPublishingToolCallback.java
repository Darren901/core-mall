package com.coremall.agent.tool;

import com.coremall.agent.dto.AgentStepEvent;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 包裝 MCP ToolCallback，在呼叫前後發佈 AgentStepEvent，
 * 讓 MCP tool call 也能顯示在 SSE step 流中。
 */
public class McpStepPublishingToolCallback implements ToolCallback {

    private static final String AGENT_NAME = "McpAgent";

    private final ToolCallback delegate;
    private final ApplicationEventPublisher publisher;

    public McpStepPublishingToolCallback(ToolCallback delegate, ApplicationEventPublisher publisher) {
        this.delegate = delegate;
        this.publisher = publisher;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        String runId = AgentRunContext.get();
        String toolName = delegate.getToolDefinition().name();

        publisher.publishEvent(new AgentStepEvent(runId, AGENT_NAME, toolName, "STARTED", null));
        try {
            String result = delegate.call(toolInput);
            publisher.publishEvent(new AgentStepEvent(runId, AGENT_NAME, toolName, "SUCCEEDED", result));
            return result;
        } catch (Exception e) {
            publisher.publishEvent(new AgentStepEvent(runId, AGENT_NAME, toolName, "FAILED", e.getMessage()));
            throw e;
        }
    }
}
