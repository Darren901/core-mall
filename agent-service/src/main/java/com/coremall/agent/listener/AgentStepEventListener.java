package com.coremall.agent.listener;

import com.coremall.agent.dto.AgentStepEvent;
import com.coremall.agent.service.AgentSinkRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

/**
 * 監聽 AgentStepEvent（由 OrderAgentTools 發佈），
 * 將 tool call 進度即時推送到對應 runId 的 SSE sink。
 */
@Component
public class AgentStepEventListener {

    private final AgentSinkRegistry sinkRegistry;

    public AgentStepEventListener(AgentSinkRegistry sinkRegistry) {
        this.sinkRegistry = sinkRegistry;
    }

    @EventListener
    public void onStepEvent(AgentStepEvent event) {
        Sinks.Many<ServerSentEvent<String>> sink = sinkRegistry.get(event.runId());
        if (sink == null) return;

        String data = String.format("{\"agentName\":\"%s\",\"toolName\":\"%s\",\"status\":\"%s\",\"payload\":\"%s\"}",
                event.agentName(), event.toolName(), event.status(),
                event.payload() != null ? event.payload().replace("\"", "'") : "");

        sink.tryEmitNext(ServerSentEvent.<String>builder()
                .event("step-" + event.status().toLowerCase())
                .data(data)
                .build());
    }
}
