package com.coremall.agent.service;

import com.coremall.agent.dto.AgentStepEvent;
import com.coremall.agent.jpa.entity.AgentRun;
import com.coremall.agent.jpa.repository.AgentRunRepository;
import com.coremall.agent.tool.AgentRunContext;
import com.coremall.agent.tool.OrderAgentTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.event.EventListener;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentRunService {

    private static final Logger log = LoggerFactory.getLogger(AgentRunService.class);

    private final AgentRunRepository agentRunRepository;
    private final ChatClient chatClient;
    private final OrderAgentTools orderAgentTools;

    /** runId → SSE sink */
    private final ConcurrentHashMap<String, Sinks.Many<ServerSentEvent<String>>> sinks =
            new ConcurrentHashMap<>();

    public AgentRunService(AgentRunRepository agentRunRepository,
                           ChatClient chatClient,
                           OrderAgentTools orderAgentTools) {
        this.agentRunRepository = agentRunRepository;
        this.chatClient = chatClient;
        this.orderAgentTools = orderAgentTools;
    }

    /**
     * 建立 AgentRun，準備 SSE sink，並啟動非同步執行。
     * @return runId（即 SSE stream 的訂閱 key）
     */
    public String startRun(String userMessage) {
        AgentRun run = agentRunRepository.save(AgentRun.create(userMessage));
        String runId = run.getId().toString();

        // 建立 SSE sink（replay 最多 100 筆，讓晚訂閱的 SSE 客戶端仍可收到近期事件）
        sinks.put(runId, Sinks.many().replay().limit(100));

        // 非同步執行 ChatClient
        executeAsync(run.getId().toString(), userMessage);

        log.info("[AgentRun] Started runId={}", runId);
        return runId;
    }

    /**
     * 訂閱指定 runId 的 SSE 事件流。
     * 若 run 已完成或 runId 不存在，立即回傳空 Flux。
     */
    public Flux<ServerSentEvent<String>> streamEvents(String runId) {
        Sinks.Many<ServerSentEvent<String>> sink = sinks.get(runId);
        if (sink == null) {
            return Flux.empty();
        }
        return sink.asFlux().timeout(Duration.ofMinutes(5));
    }

    /**
     * 監聽 AgentStepEvent（由 OrderAgentTools 發佈），
     * 推送對應的 SSE 事件到 sink。
     */
    @EventListener
    public void onStepEvent(AgentStepEvent event) {
        Sinks.Many<ServerSentEvent<String>> sink = sinks.get(event.runId());
        if (sink == null) return;

        String data = String.format("{\"toolName\":\"%s\",\"status\":\"%s\",\"payload\":\"%s\"}",
                event.toolName(), event.status(),
                event.payload() != null ? event.payload().replace("\"", "'") : "");

        sink.tryEmitNext(ServerSentEvent.<String>builder()
                .event("step-" + event.status().toLowerCase())
                .data(data)
                .build());
    }

    @Async("stepAsyncExecutor")
    protected void executeAsync(String runId, String userMessage) {
        AgentRunContext.set(runId);
        try {
            log.info("[AgentRun] Executing runId={}", runId);
            String reply = chatClient.prompt()
                    .user(userMessage)
                    .tools(orderAgentTools)
                    .call()
                    .content();

            // 更新 AgentRun 狀態
            agentRunRepository.findById(java.util.UUID.fromString(runId)).ifPresent(run -> {
                run.complete(reply);
                agentRunRepository.save(run);
            });

            // 推送 run-completed 事件
            Sinks.Many<ServerSentEvent<String>> sink = sinks.get(runId);
            if (sink != null) {
                sink.tryEmitNext(ServerSentEvent.<String>builder()
                        .event("run-completed")
                        .data("{\"reply\":\"" + reply.replace("\"", "'") + "\"}")
                        .build());
                sink.tryEmitComplete();
                sinks.remove(runId);
            }
            log.info("[AgentRun] Completed runId={}", runId);
        } catch (Exception e) {
            log.error("[AgentRun] Failed runId={}", runId, e);
            agentRunRepository.findById(java.util.UUID.fromString(runId)).ifPresent(run -> {
                run.fail(e.getMessage());
                agentRunRepository.save(run);
            });
            Sinks.Many<ServerSentEvent<String>> sink = sinks.remove(runId);
            if (sink != null) {
                sink.tryEmitNext(ServerSentEvent.<String>builder()
                        .event("run-failed")
                        .data("{\"error\":\"" + e.getMessage() + "\"}")
                        .build());
                sink.tryEmitComplete();
            }
        } finally {
            AgentRunContext.clear();
        }
    }
}
