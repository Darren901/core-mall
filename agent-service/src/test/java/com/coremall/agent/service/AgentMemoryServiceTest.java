package com.coremall.agent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentMemoryService - 對話記憶清除")
class AgentMemoryServiceTest {

    @Mock
    private ChatMemory chatMemory;

    @InjectMocks
    private AgentMemoryService agentMemoryService;

    @Test
    @DisplayName("clear：呼叫 chatMemory.clear 並傳入正確 userId")
    void shouldClearChatMemoryForUserId() {
        agentMemoryService.clear("U001");

        verify(chatMemory).clear("U001");
    }

    @Test
    @DisplayName("clear：userId 不存在時不拋例外（冪等）")
    void shouldNotThrowWhenClearingNonExistentUserId() {
        assertThatCode(() -> agentMemoryService.clear("U999"))
                .doesNotThrowAnyException();
    }
}
