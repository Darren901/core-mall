package com.coremall.agent.service;

import com.coremall.agent.jpa.entity.AgentStep;
import com.coremall.agent.jpa.repository.AgentStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * AgentStep DB 寫入服務：
 * - saveStarted：同步建立 STARTED 記錄（確保後續 update 找得到）
 * - saveCompleted：@Async 更新為 SUCCEEDED / FAILED
 */
@Service
public class AsyncStepService {

    private static final Logger log = LoggerFactory.getLogger(AsyncStepService.class);

    private final AgentStepRepository stepRepository;

    public AsyncStepService(AgentStepRepository stepRepository) {
        this.stepRepository = stepRepository;
    }

    /**
     * 同步建立 STARTED step 記錄，確保在 SUCCEEDED/FAILED update 前已持久化。
     */
    @Transactional
    public void saveStarted(String runId, String toolName) {
        AgentStep step = AgentStep.start(UUID.fromString(runId), toolName);
        stepRepository.save(step);
        log.debug("[AsyncStep] STARTED run={} tool={}", runId, toolName);
    }

    /**
     * @Async 更新最新 STARTED step 為 SUCCEEDED 或 FAILED，不阻塞 tool call。
     */
    @Async("stepAsyncExecutor")
    @Transactional
    public void saveCompleted(String runId, String toolName, String status, String payloadOrError) {
        stepRepository.findByRunIdOrderByCreatedAtAsc(UUID.fromString(runId)).stream()
                .filter(s -> s.getToolName().equals(toolName) && "STARTED".equals(s.getStatus()))
                .reduce((first, second) -> second)
                .ifPresent(step -> {
                    if ("SUCCEEDED".equals(status)) {
                        step.succeed(payloadOrError);
                    } else {
                        step.fail(payloadOrError);
                    }
                    stepRepository.save(step);
                    log.debug("[AsyncStep] {} run={} tool={}", status, runId, toolName);
                });
    }
}
