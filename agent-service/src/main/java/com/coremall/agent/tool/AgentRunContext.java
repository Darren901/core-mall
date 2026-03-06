package com.coremall.agent.tool;

/**
 * 以 ThreadLocal 傳遞目前執行中的 AgentRun ID。
 * AgentRunService 在執行前設定，tool 方法讀取以確定冪等 key 範圍。
 */
public final class AgentRunContext {

    private static final ThreadLocal<String> RUN_ID = new ThreadLocal<>();

    private AgentRunContext() {}

    public static void set(String runId) {
        RUN_ID.set(runId);
    }

    public static String get() {
        return RUN_ID.get();
    }

    public static void clear() {
        RUN_ID.remove();
    }
}
