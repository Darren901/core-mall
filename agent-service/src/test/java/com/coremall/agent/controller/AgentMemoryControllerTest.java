package com.coremall.agent.controller;

import com.coremall.agent.dto.MessageRecord;
import com.coremall.agent.service.AgentMemoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(AgentMemoryController.class)
@DisplayName("AgentMemoryController")
class AgentMemoryControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AgentMemoryService agentMemoryService;

    @Test
    @DisplayName("DELETE /memory/{userId} - 清除成功回傳 204 No Content")
    void shouldReturn204OnSuccessfulClear() {
        webTestClient.delete()
                .uri("/api/v1/agent/memory/U001")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DisplayName("DELETE /memory/{userId} - 委派正確 userId 給 AgentMemoryService")
    void shouldDelegateClearToServiceWithCorrectUserId() {
        webTestClient.delete()
                .uri("/api/v1/agent/memory/U001")
                .exchange()
                .expectStatus().isNoContent();

        verify(agentMemoryService).clear("U001");
    }

    @Test
    @DisplayName("GET /memory - 回傳該用戶對話歷史 200 OK")
    void shouldReturnHistoryForUser() {
        when(agentMemoryService.getHistory("U001")).thenReturn(List.of(
                new MessageRecord("user", "幫我訂蘋果"),
                new MessageRecord("assistant", "好的，請問數量？")
        ));

        webTestClient.get()
                .uri("/api/v1/agent/memory")
                .header("X-User-Id", "U001")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data[0].role").isEqualTo("user")
                .jsonPath("$.data[0].content").isEqualTo("幫我訂蘋果")
                .jsonPath("$.data[1].role").isEqualTo("assistant");
    }

    @Test
    @DisplayName("GET /memory - 無歷史時回傳空陣列")
    void shouldReturnEmptyListWhenNoHistory() {
        when(agentMemoryService.getHistory("U999")).thenReturn(List.of());

        webTestClient.get()
                .uri("/api/v1/agent/memory")
                .header("X-User-Id", "U999")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("GET /memory - 缺少 X-User-Id 回傳 400")
    void shouldReturn400WhenUserIdHeaderMissing() {
        webTestClient.get()
                .uri("/api/v1/agent/memory")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
