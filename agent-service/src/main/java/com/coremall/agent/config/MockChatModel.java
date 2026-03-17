package com.coremall.agent.config;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * loadtest profile 專用的假 ChatModel。
 * 回傳固定回應，不呼叫外部 LLM，用於壓測驗證架構（SSE、冪等、分散式鎖）而不產生 API 費用。
 */
public class MockChatModel implements ChatModel {

    static final String MOCK_REPLY = "[LOADTEST] 這是 MockChatModel 的固定回應，不會呼叫真實 LLM。";

    @Override
    public ChatResponse call(Prompt prompt) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(MOCK_REPLY))));
    }
}
