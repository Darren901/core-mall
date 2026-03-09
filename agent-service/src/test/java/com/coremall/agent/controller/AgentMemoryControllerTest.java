package com.coremall.agent.controller;

import com.coremall.agent.service.AgentMemoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.verify;

@WebFluxTest(AgentMemoryController.class)
@DisplayName("DELETE /api/v1/agent/memory/{userId}")
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
}
