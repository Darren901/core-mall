package com.coremall.agent.controller;

import com.coremall.agent.service.AgentMemoryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentMemoryController {

    private final AgentMemoryService agentMemoryService;

    public AgentMemoryController(AgentMemoryService agentMemoryService) {
        this.agentMemoryService = agentMemoryService;
    }

    @DeleteMapping("/memory/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearMemory(@PathVariable String userId) {
        agentMemoryService.clear(userId);
    }
}
