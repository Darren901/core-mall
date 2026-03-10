package com.coremall.agent.service;

import com.coremall.agent.jpa.repository.AgentRunRepository;
import com.coremall.agent.tool.AgentRunContext;
import com.coremall.agent.tool.OrderAgentTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;

/**
 * 持有 @Async 執行邏輯的獨立 bean。
 * 將此方法與 AgentRunService 分離，確保透過 Spring AOP proxy 呼叫，
 * 使 @Async 正確生效，不在 Netty reactor-http-nio 執行緒上阻塞。
 */
@Component
public class AgentRunExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentRunExecutor.class);

    private final ChatClient chatClient;
    private final OrderAgentTools orderAgentTools;
    private final AgentRunRepository agentRunRepository;
    private final AgentSinkRegistry sinkRegistry;
    private final ObjectMapper objectMapper;

    public AgentRunExecutor(ChatClient chatClient,
                            OrderAgentTools orderAgentTools,
                            AgentRunRepository agentRunRepository,
                            AgentSinkRegistry sinkRegistry,
                            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.orderAgentTools = orderAgentTools;
        this.agentRunRepository = agentRunRepository;
        this.sinkRegistry = sinkRegistry;
        this.objectMapper = objectMapper;
    }

    @Async("stepAsyncExecutor")
    public void execute(String runId, String userId, String userMessage) {
        AgentRunContext.set(runId);
        try {
            log.info("[AgentRun] Executing runId={} userId={}", runId, userId);
            String reply = chatClient.prompt()
                    .user(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId.replace("-", "")))
                    .tools(orderAgentTools)
                    .call()
                    .content();

            agentRunRepository.findById(UUID.fromString(runId)).ifPresent(run -> {
                run.complete(reply);
                agentRunRepository.save(run);
            });

            Sinks.Many<ServerSentEvent<String>> sink = sinkRegistry.remove(runId);
            if (sink != null) {
                sink.tryEmitNext(ServerSentEvent.<String>builder()
                        .event("run-completed")
                        .data(toJson(Map.of("reply", reply != null ? reply : "")))
                        .build());
                sink.tryEmitComplete();
            }
            log.info("[AgentRun] Completed runId={}", runId);
        } catch (Exception e) {
            log.error("[AgentRun] Failed runId={}", runId, e);
            agentRunRepository.findById(UUID.fromString(runId)).ifPresent(run -> {
                run.fail(e.getMessage());
                agentRunRepository.save(run);
            });
            Sinks.Many<ServerSentEvent<String>> sink = sinkRegistry.remove(runId);
            if (sink != null) {
                sink.tryEmitNext(ServerSentEvent.<String>builder()
                        .event("run-failed")
                        .data(toJson(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error")))
                        .build());
                sink.tryEmitComplete();
            }
        } finally {
            AgentRunContext.clear();
        }
    }

    private String toJson(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("[AgentRun] Failed to serialize payload", e);
            return "{\"error\":\"serialization failed\"}";
        }
    }
}
