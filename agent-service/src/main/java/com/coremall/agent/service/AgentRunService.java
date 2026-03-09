package com.coremall.agent.service;

import com.coremall.agent.dto.AgentStepEvent;
import com.coremall.agent.jpa.entity.AgentRun;
import com.coremall.agent.jpa.repository.AgentRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class AgentRunService {

    private static final Logger log = LoggerFactory.getLogger(AgentRunService.class);

    private final AgentRunRepository agentRunRepository;
    private final AgentSinkRegistry sinkRegistry;
    private final AgentRunExecutor agentRunExecutor;

    public AgentRunService(AgentRunRepository agentRunRepository,
                           AgentSinkRegistry sinkRegistry,
                           AgentRunExecutor agentRunExecutor) {
        this.agentRunRepository = agentRunRepository;
        this.sinkRegistry = sinkRegistry;
        this.agentRunExecutor = agentRunExecutor;
    }

    /**
     * 建立 AgentRun，準備 SSE sink，並觸發非同步執行。
     * agentRunExecutor.execute() 透過獨立 bean 呼叫，確保 @Async proxy 生效。
     *
     * @return runId（即 SSE stream 的訂閱 key）
     */
    public String startRun(String userId, String userMessage) {
        AgentRun run = agentRunRepository.save(AgentRun.create(userMessage));
        String runId = run.getId().toString();

        sinkRegistry.register(runId);
        agentRunExecutor.execute(runId, userId, userMessage);

        log.info("[AgentRun] Started runId={} userId={}", runId, userId);
        return runId;
    }

    /**
     * 訂閱指定 runId 的 SSE 事件流。
     * 若 runId 不存在或已完成，立即回傳空 Flux。
     */
    public Flux<ServerSentEvent<String>> streamEvents(String runId) {
        return sinkRegistry.stream(runId);
    }

    /**
     * 監聽 AgentStepEvent（由 OrderAgentTools 發佈），推送對應的 SSE 事件到 sink。
     */
    @EventListener
    public void onStepEvent(AgentStepEvent event) {
        Sinks.Many<ServerSentEvent<String>> sink = sinkRegistry.get(event.runId());
        if (sink == null) return;

        String data = String.format("{\"toolName\":\"%s\",\"status\":\"%s\",\"payload\":\"%s\"}",
                event.toolName(), event.status(),
                event.payload() != null ? event.payload().replace("\"", "'") : "");

        sink.tryEmitNext(ServerSentEvent.<String>builder()
                .event("step-" + event.status().toLowerCase())
                .data(data)
                .build());
    }
}
