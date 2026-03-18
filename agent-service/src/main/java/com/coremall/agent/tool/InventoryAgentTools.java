package com.coremall.agent.tool;

import com.coremall.agent.client.InventoryServiceClient;
import com.coremall.agent.dto.AgentStepEvent;
import com.coremall.agent.dto.InventoryResult;
import com.coremall.agent.service.AsyncStepService;
import com.coremall.sharedkernel.exception.ServiceTransientException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * LLM tool methods for inventory queries.
 * 每個 @Tool 方法流程：
 *   1. 同步檢查 Redis 冪等鍵（step:<runId>:<toolName>:<params>）
 *   2. 呼叫 inventory-service（有結果才繼續）
 *   3. @Async 寫 AgentStep（STARTED → SUCCEEDED / FAILED）
 *   4. 發佈 AgentStepEvent 更新 SSE stream
 *   5. 存冪等鍵到 Redis
 */
@Component
public class InventoryAgentTools {

    private static final Logger log = LoggerFactory.getLogger(InventoryAgentTools.class);
    private static final Duration STEP_IDEM_TTL = Duration.ofHours(1);

    private final InventoryServiceClient inventoryServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final AsyncStepService asyncStepService;
    private final ApplicationEventPublisher publisher;
    private final Tracer tracer;

    public InventoryAgentTools(InventoryServiceClient inventoryServiceClient,
                               StringRedisTemplate redisTemplate,
                               AsyncStepService asyncStepService,
                               ApplicationEventPublisher publisher,
                               Tracer tracer) {
        this.inventoryServiceClient = inventoryServiceClient;
        this.redisTemplate = redisTemplate;
        this.asyncStepService = asyncStepService;
        this.publisher = publisher;
        this.tracer = tracer;
    }

    @Tool(description = "查詢指定商品的庫存數量，回傳庫存狀態。")
    public String checkInventory(
            @ToolParam(description = "要查詢庫存的商品名稱") String productName) {
        String tool = "checkInventory";
        String runId = AgentRunContext.get();
        String stepKey = stepKey(runId, tool, productName);

        String cached = redisTemplate.opsForValue().get(stepKey);
        if (cached != null) {
            log.info("[Tool] {} cache hit key={}", tool, stepKey);
            return cached;
        }
        asyncStepService.saveStarted(runId, tool);
        publisher.publishEvent(new AgentStepEvent(runId, tool, "STARTED", null));

        Span span = tracer.nextSpan()
                .name("agent.tool.checkInventory")
                .tag("runId", runId != null ? runId : "")
                .start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            InventoryResult result = inventoryServiceClient.getStock(productName);
            String availability = result.quantity() > 0 ? "有貨" : "缺貨";
            String response = String.format("%s 庫存：%d 件，%s", result.productName(), result.quantity(), availability);

            asyncStepService.saveCompleted(runId, tool, "SUCCEEDED", response);
            publisher.publishEvent(new AgentStepEvent(runId, tool, "SUCCEEDED", response));
            redisTemplate.opsForValue().set(stepKey, response, STEP_IDEM_TTL);

            log.info("[Tool] {} succeeded productName={} quantity={}", tool, productName, result.quantity());
            return response;
        } catch (Exception e) {
            String error = errorPrefix(e) + "查詢庫存失敗：" + e.getMessage();
            asyncStepService.saveCompleted(runId, tool, "FAILED", e.getMessage());
            publisher.publishEvent(new AgentStepEvent(runId, tool, "FAILED", error));
            log.warn("[Tool] {} failed: {}", tool, e.getMessage());
            return error;
        } finally {
            span.end();
        }
    }

    private String errorPrefix(Throwable e) {
        return (e instanceof ServiceTransientException) ? "TRANSIENT_ERROR|" : "BUSINESS_ERROR|";
    }

    private String stepKey(String runId, String toolName, String... params) {
        return "step:" + runId + ":" + toolName + ":" + String.join(":", params);
    }
}
