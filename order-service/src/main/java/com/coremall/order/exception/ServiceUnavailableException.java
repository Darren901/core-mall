package com.coremall.order.exception;

public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String serviceName) {
        super("服務暫時無法使用：" + serviceName);
    }
}
