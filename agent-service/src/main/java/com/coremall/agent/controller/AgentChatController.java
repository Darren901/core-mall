package com.coremall.agent.controller;

import com.coremall.agent.dto.ChatInitResponse;
import com.coremall.agent.dto.ChatRequest;
import com.coremall.agent.service.AgentRunService;
import com.coremall.sharedkernel.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentChatController {

    private final AgentRunService agentRunService;

    public AgentChatController(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    /**
     * 提交聊天請求，立即回傳 202 Accepted 與 runId。
     * ChatClient 執行（含 tool call）在背景非同步進行。
     */
    @PostMapping("/chat")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ChatInitResponse> chat(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody ChatRequest request) {
        String runId = agentRunService.startRun(userId, request.message(), request.model());
        return ApiResponse.success(new ChatInitResponse(runId));
    }

    /**
     * 訂閱 Agent 執行進度的 SSE 事件串流。
     * 事件類型：step-started / step-succeeded / step-failed / run-completed / run-failed
     */
    @GetMapping(value = "/sessions/{sessionId}/stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@PathVariable String sessionId) {
        return agentRunService.streamEvents(sessionId);
    }
}
