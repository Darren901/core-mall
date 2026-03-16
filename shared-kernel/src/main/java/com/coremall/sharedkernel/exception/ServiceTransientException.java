package com.coremall.sharedkernel.exception;

/**
 * 呼叫下游服務時遇到暫時性錯誤（5xx / IOException），可重試。
 */
public class ServiceTransientException extends RuntimeException {

    public ServiceTransientException(String message) {
        super(message);
    }

    public ServiceTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
