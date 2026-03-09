package com.coremall.agent.controller;

import com.coremall.agent.dto.MessageRecord;
import com.coremall.agent.service.AgentMemoryService;
import com.coremall.sharedkernel.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentMemoryController {

    private final AgentMemoryService agentMemoryService;

    public AgentMemoryController(AgentMemoryService agentMemoryService) {
        this.agentMemoryService = agentMemoryService;
    }

    @GetMapping("/memory")
    public ApiResponse<List<MessageRecord>> getHistory(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(agentMemoryService.getHistory(userId));
    }

    @DeleteMapping("/memory/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearMemory(@PathVariable String userId) {
        agentMemoryService.clear(userId);
    }
}
