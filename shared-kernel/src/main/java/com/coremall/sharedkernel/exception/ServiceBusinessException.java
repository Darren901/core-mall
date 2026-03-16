package com.coremall.sharedkernel.exception;

/**
 * 呼叫下游服務時遭業務規則拒絕（4xx），不可重試。
 */
public class ServiceBusinessException extends RuntimeException {

    public ServiceBusinessException(String message) {
        super(message);
    }

    public ServiceBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
