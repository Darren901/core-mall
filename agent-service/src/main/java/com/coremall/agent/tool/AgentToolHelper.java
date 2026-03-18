package com.coremall.agent.tool;

import com.coremall.sharedkernel.exception.ServiceTransientException;

/**
 * Tool 層共用工具方法。
 */
class AgentToolHelper {

    private AgentToolHelper() {}

    static String errorPrefix(Throwable e) {
        return (e instanceof ServiceTransientException) ? "TRANSIENT_ERROR|" : "BUSINESS_ERROR|";
    }

    static String stepKey(String runId, String toolName, String... params) {
        return "step:" + runId + ":" + toolName + ":" + String.join(":", params);
    }
}
