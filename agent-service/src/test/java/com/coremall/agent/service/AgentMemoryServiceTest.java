package com.coremall.agent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentMemoryService - 對話記憶清除")
class AgentMemoryServiceTest {

    @Mock
    private ChatMemory chatMemory;

    @Mock
    private VectorStore vectorStore;

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

    @Test
    @DisplayName("clear：呼叫 vectorStore.delete 並傳入包含 userId 的 filterExpression")
    void shouldDeleteVectorStoreWithUserIdFilter() {
        agentMemoryService.clear("U001");

        ArgumentCaptor<Filter.Expression> captor = ArgumentCaptor.forClass(Filter.Expression.class);
        verify(vectorStore).delete(captor.capture());

        String filterStr = captor.getValue().toString();
        assertThat(filterStr).contains("U001");
    }

    @Test
    @DisplayName("clear：先清短期記憶再清長期向量記憶（順序正確）")
    void shouldClearChatMemoryBeforeVectorStore() {
        var order = org.mockito.Mockito.inOrder(chatMemory, vectorStore);

        agentMemoryService.clear("U001");

        order.verify(chatMemory).clear("U001");
        order.verify(vectorStore).delete(any(Filter.Expression.class));
    }
}
