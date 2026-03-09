package com.coremall.agent.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Redis-backed ChatMemory 實作。
 * 使用 StringRedisTemplate 將對話歷史以 JSON 格式儲存，
 * key 格式：chat-memory:{conversationId}
 * 取代 RedisChatMemoryRepository（Spring AI 1.1.2 尚未提供）。
 */
@Component
public class RedisChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemory.class);
    private static final String KEY_PREFIX = "chat-memory:";
    private static final int MAX_MESSAGES = 20;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatMemory(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = KEY_PREFIX + conversationId;
        List<Map<String, String>> existing = loadRaw(key);
        messages.forEach(m -> existing.add(Map.of(
                "type", m.getMessageType().name(),
                "content", m.getText()
        )));
        List<Map<String, String>> windowed = existing.size() > MAX_MESSAGES
                ? existing.subList(existing.size() - MAX_MESSAGES, existing.size())
                : existing;
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(windowed));
        } catch (Exception e) {
            log.error("[ChatMemory] Failed to save messages for conversationId={}", conversationId, e);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        List<Map<String, String>> raw = loadRaw(key);
        return raw.stream().map(this::toMessage).collect(Collectors.toList());
    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
        log.info("[ChatMemory] Cleared memory for conversationId={}", conversationId);
    }

    private List<Map<String, String>> loadRaw(String key) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            log.warn("[ChatMemory] Failed to deserialize messages for key={}", key, e);
            return new ArrayList<>();
        }
    }

    private Message toMessage(Map<String, String> raw) {
        String type = raw.getOrDefault("type", MessageType.USER.name());
        String content = raw.getOrDefault("content", "");
        if (MessageType.ASSISTANT.name().equals(type)) {
            return new AssistantMessage(content);
        }
        return new UserMessage(content);
    }
}
