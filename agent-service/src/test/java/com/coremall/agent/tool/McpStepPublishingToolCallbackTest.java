package com.coremall.agent.tool;

import com.coremall.agent.dto.AgentStepEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("McpStepPublishingToolCallback - MCP tool call 的 AgentStepEvent 發佈")
class McpStepPublishingToolCallbackTest {

    @Mock
    private ToolCallback delegate;

    @Mock
    private ApplicationEventPublisher publisher;

    private McpStepPublishingToolCallback wrapper;

    @BeforeEach
    void setUp() {
        ToolDefinition definition = ToolDefinition.builder()
                .name("read_file")
                .description("Read a file")
                .inputSchema("{}")
                .build();
        when(delegate.getToolDefinition()).thenReturn(definition);
        AgentRunContext.set("run-test-001");
        wrapper = new McpStepPublishingToolCallback(delegate, publisher);
    }

    @AfterEach
    void tearDown() {
        AgentRunContext.clear();
    }

    @Test
    @DisplayName("call 成功：先發 STARTED，再發 SUCCEEDED，回傳 delegate 結果")
    void shouldPublishStartedThenSucceededOnSuccess() {
        when(delegate.call(any())).thenReturn("file content");

        String result = wrapper.call("{\"path\":\"/data/products.json\"}");

        assertThat(result).isEqualTo("file content");

        ArgumentCaptor<AgentStepEvent> captor = ArgumentCaptor.forClass(AgentStepEvent.class);
        verify(publisher, org.mockito.Mockito.times(2)).publishEvent(captor.capture());

        AgentStepEvent started = captor.getAllValues().get(0);
        assertThat(started.toolName()).isEqualTo("read_file");
        assertThat(started.status()).isEqualTo("STARTED");
        assertThat(started.runId()).isEqualTo("run-test-001");

        AgentStepEvent succeeded = captor.getAllValues().get(1);
        assertThat(succeeded.toolName()).isEqualTo("read_file");
        assertThat(succeeded.status()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.payload()).isEqualTo("file content");
    }

    @Test
    @DisplayName("call 失敗：先發 STARTED，再發 FAILED，並重新拋出例外")
    void shouldPublishStartedThenFailedOnException() {
        when(delegate.call(any())).thenThrow(new RuntimeException("permission denied"));

        assertThatThrownBy(() -> wrapper.call("{\"path\":\"/etc/passwd\"}"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("permission denied");

        ArgumentCaptor<AgentStepEvent> captor = ArgumentCaptor.forClass(AgentStepEvent.class);
        verify(publisher, org.mockito.Mockito.times(2)).publishEvent(captor.capture());

        assertThat(captor.getAllValues().get(0).status()).isEqualTo("STARTED");
        AgentStepEvent failed = captor.getAllValues().get(1);
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.payload()).contains("permission denied");
    }

    @Test
    @DisplayName("getToolDefinition 委派給 delegate")
    void shouldDelegateGetToolDefinition() {
        assertThat(wrapper.getToolDefinition().name()).isEqualTo("read_file");
    }

    @Test
    @DisplayName("runId 為 null 時（AgentRunContext 未設定）仍正常執行")
    void shouldHandleNullRunId() {
        AgentRunContext.clear();
        when(delegate.call(any())).thenReturn("ok");

        String result = wrapper.call("{}");

        assertThat(result).isEqualTo("ok");
    }
}
