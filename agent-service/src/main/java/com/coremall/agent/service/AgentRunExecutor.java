package com.coremall.agent.service;

import com.coremall.agent.jpa.repository.AgentRunRepository;
import com.coremall.agent.tool.AgentRunContext;
import com.coremall.agent.tool.OrderAgentTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
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

    private final ChatClientFactory chatClientFactory;
    private final OrderAgentTools orderAgentTools;
    private final AgentRunRepository agentRunRepository;
    private final AgentSinkRegistry sinkRegistry;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public AgentRunExecutor(ChatClientFactory chatClientFactory,
                            OrderAgentTools orderAgentTools,
                            AgentRunRepository agentRunRepository,
                            AgentSinkRegistry sinkRegistry,
                            ObjectMapper objectMapper,
                            Tracer tracer) {
        this.chatClientFactory = chatClientFactory;
        this.orderAgentTools = orderAgentTools;
        this.agentRunRepository = agentRunRepository;
        this.sinkRegistry = sinkRegistry;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    @Async("stepAsyncExecutor")
    public void execute(String runId, String userId, String userMessage, String model) {
        Span span = tracer.nextSpan()
                .name("agent.run")
                .tag("runId", runId)
                .tag("userId", userId)
                .start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            AgentRunContext.set(runId);
            log.info("[AgentRun] Executing runId={} userId={} model={}", runId, userId, model);
            ChatClient chatClient = chatClientFactory.getClient(model);
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
                        .data(toJson(Map.of("error", toFriendlyError(e))))
                        .build());
                sink.tryEmitComplete();
            }
        } finally {
            span.end();
            AgentRunContext.clear();
        }
    }

    private String toFriendlyError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (e instanceof NonTransientAiException) {
            if (msg.contains("credit balance") || msg.contains("billing")) {
                return "AI 模型服務授權失敗，請聯絡管理員";
            }
            if (msg.contains("401") || msg.contains("invalid_api_key")) {
                return "AI 模型 API 金鑰無效，請聯絡管理員";
            }
            return "AI 模型請求失敗，請稍後再試";
        }
        if (e instanceof TransientAiException) {
            if (msg.contains("429")) {
                return "AI 模型請求過於頻繁，請稍後再試";
            }
            return "AI 模型服務暫時不可用，請稍後再試";
        }
        return "Agent 執行失敗，請稍後再試";
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
