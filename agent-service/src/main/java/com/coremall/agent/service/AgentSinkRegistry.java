package com.coremall.agent.service;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE Sink 的生命週期管理：register（建立）、get（讀取）、remove（移除）、stream（訂閱）。
 * 抽離為獨立 bean，讓 AgentRunService 與 AgentRunExecutor 可共用而不產生循環依賴。
 */
@Component
public class AgentSinkRegistry {

    private final ConcurrentHashMap<String, Sinks.Many<ServerSentEvent<String>>> sinks =
            new ConcurrentHashMap<>();

    public void register(String runId) {
        sinks.put(runId, Sinks.many().replay().limit(100));
    }

    public Sinks.Many<ServerSentEvent<String>> get(String runId) {
        return sinks.get(runId);
    }

    public Sinks.Many<ServerSentEvent<String>> remove(String runId) {
        return sinks.remove(runId);
    }

    public Flux<ServerSentEvent<String>> stream(String runId) {
        Sinks.Many<ServerSentEvent<String>> sink = sinks.get(runId);
        if (sink == null) {
            return Flux.empty();
        }
        return sink.asFlux().timeout(Duration.ofMinutes(5));
    }
}
