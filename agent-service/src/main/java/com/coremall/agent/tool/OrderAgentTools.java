package com.coremall.agent.tool;

import com.coremall.agent.client.OrderServiceClient;
import com.coremall.agent.dto.AgentStepEvent;
import com.coremall.agent.dto.OrderResult;
import com.coremall.agent.service.AsyncStepService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * LLM tool methods for order management.
 * 每個 @Tool 方法流程：
 *   1. 同步檢查 Redis 冪等鍵（step:<runId>:<toolName>:<params>）
 *   2. 呼叫 order-service（有結果才繼續）
 *   3. @Async 寫 AgentStep（STARTED → SUCCEEDED / FAILED）
 *   4. 發佈 AgentStepEvent 更新 SSE stream
 *   5. 存冪等鍵到 Redis
 */
@Component
public class OrderAgentTools {

    private static final Logger log = LoggerFactory.getLogger(OrderAgentTools.class);
    private static final Duration STEP_IDEM_TTL = Duration.ofHours(1);

    private final OrderServiceClient orderServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final AsyncStepService asyncStepService;
    private final ApplicationEventPublisher publisher;

    public OrderAgentTools(OrderServiceClient orderServiceClient,
                           StringRedisTemplate redisTemplate,
                           AsyncStepService asyncStepService,
                           ApplicationEventPublisher publisher) {
        this.orderServiceClient = orderServiceClient;
        this.redisTemplate = redisTemplate;
        this.asyncStepService = asyncStepService;
        this.publisher = publisher;
    }

    @Tool(description = "建立新訂單。需要 userId（用戶 ID）、productName（商品名稱）、quantity（數量，正整數）。回傳訂單 ID。")
    public String createOrder(String userId, String productName, int quantity) {
        String tool = "createOrder";
        String stepKey = stepKey(tool, userId, productName, String.valueOf(quantity));

        String cached = redisTemplate.opsForValue().get(stepKey);
        if (cached != null) {
            log.info("[Tool] {} cache hit key={}", tool, stepKey);
            return cached;
        }

        String runId = AgentRunContext.get();
        asyncStepService.saveStarted(runId, tool);
        publisher.publishEvent(new AgentStepEvent(runId, tool, "STARTED", null));

        try {
            String idemKey = runId + "-" + tool + "-" + userId;
            OrderResult result = orderServiceClient.createOrder(userId, productName, quantity, idemKey);
            String response = "訂單已建立，ID: " + result.id();

            asyncStepService.saveCompleted(runId, tool, "SUCCEEDED", response);
            publisher.publishEvent(new AgentStepEvent(runId, tool, "SUCCEEDED", response));
            redisTemplate.opsForValue().set(stepKey, response, STEP_IDEM_TTL);

            log.info("[Tool] {} succeeded orderId={}", tool, result.id());
            return response;
        } catch (Exception e) {
            String error = "訂單建立失敗：" + e.getMessage();
            asyncStepService.saveCompleted(runId, tool, "FAILED", e.getMessage());
            publisher.publishEvent(new AgentStepEvent(runId, tool, "FAILED", error));
            log.warn("[Tool] {} failed: {}", tool, e.getMessage());
            return error;
        }
    }

    @Tool(description = "更新現有訂單的商品名稱或數量。需要 orderId（訂單 ID）；productName 和 quantity 至少提供一個。")
    public String updateOrder(String orderId, String productName, int quantity) {
        String tool = "updateOrder";
        String stepKey = stepKey(tool, orderId, productName, String.valueOf(quantity));

        String cached = redisTemplate.opsForValue().get(stepKey);
        if (cached != null) {
            log.info("[Tool] {} cache hit key={}", tool, stepKey);
            return cached;
        }

        String runId = AgentRunContext.get();
        asyncStepService.saveStarted(runId, tool);
        publisher.publishEvent(new AgentStepEvent(runId, tool, "STARTED", null));

        try {
            String idemKey = runId + "-" + tool + "-" + orderId;
            OrderResult result = orderServiceClient.updateOrder(orderId, productName, quantity, idemKey);
            String response = "訂單已更新，ID: " + result.id() + "，數量: " + result.quantity();

            asyncStepService.saveCompleted(runId, tool, "SUCCEEDED", response);
            publisher.publishEvent(new AgentStepEvent(runId, tool, "SUCCEEDED", response));
            redisTemplate.opsForValue().set(stepKey, response, STEP_IDEM_TTL);

            return response;
        } catch (Exception e) {
            String error = "訂單更新失敗：" + e.getMessage();
            asyncStepService.saveCompleted(runId, tool, "FAILED", e.getMessage());
            publisher.publishEvent(new AgentStepEvent(runId, tool, "FAILED", error));
            return error;
        }
    }

    @Tool(description = "取消訂單。需要 orderId（訂單 ID）。")
    public String cancelOrder(String orderId) {
        String tool = "cancelOrder";
        String stepKey = stepKey(tool, orderId);

        String cached = redisTemplate.opsForValue().get(stepKey);
        if (cached != null) {
            log.info("[Tool] {} cache hit key={}", tool, stepKey);
            return cached;
        }

        String runId = AgentRunContext.get();
        asyncStepService.saveStarted(runId, tool);
        publisher.publishEvent(new AgentStepEvent(runId, tool, "STARTED", null));

        try {
            String idemKey = runId + "-" + tool + "-" + orderId;
            orderServiceClient.cancelOrder(orderId, idemKey);
            String response = "訂單 " + orderId + " 已取消";

            asyncStepService.saveCompleted(runId, tool, "SUCCEEDED", response);
            publisher.publishEvent(new AgentStepEvent(runId, tool, "SUCCEEDED", response));
            redisTemplate.opsForValue().set(stepKey, response, STEP_IDEM_TTL);

            return response;
        } catch (Exception e) {
            String error = "取消訂單失敗：" + e.getMessage();
            asyncStepService.saveCompleted(runId, tool, "FAILED", e.getMessage());
            publisher.publishEvent(new AgentStepEvent(runId, tool, "FAILED", error));
            return error;
        }
    }

    @Tool(description = "查詢訂單狀態。需要 orderId（訂單 ID）。回傳訂單詳細資訊。")
    public String getOrderStatus(String orderId) {
        String tool = "getOrderStatus";
        String stepKey = stepKey(tool, orderId);

        String cached = redisTemplate.opsForValue().get(stepKey);
        if (cached != null) {
            log.info("[Tool] {} cache hit key={}", tool, stepKey);
            return cached;
        }

        String runId = AgentRunContext.get();
        asyncStepService.saveStarted(runId, tool);
        publisher.publishEvent(new AgentStepEvent(runId, tool, "STARTED", null));

        try {
            OrderResult result = orderServiceClient.getOrder(orderId);
            String response = String.format("訂單 %s 狀態：%s，商品：%s，數量：%d",
                    result.id(), result.status(), result.productName(), result.quantity());

            asyncStepService.saveCompleted(runId, tool, "SUCCEEDED", response);
            publisher.publishEvent(new AgentStepEvent(runId, tool, "SUCCEEDED", response));
            redisTemplate.opsForValue().set(stepKey, response, STEP_IDEM_TTL);

            return response;
        } catch (Exception e) {
            String error = "查詢訂單失敗：" + e.getMessage();
            asyncStepService.saveCompleted(runId, tool, "FAILED", e.getMessage());
            publisher.publishEvent(new AgentStepEvent(runId, tool, "FAILED", error));
            return error;
        }
    }

    private String stepKey(String toolName, String... params) {
        String runId = AgentRunContext.get();
        return "step:" + runId + ":" + toolName + ":" + String.join(":", params);
    }
}
