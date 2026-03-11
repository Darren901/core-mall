package com.coremall.order.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productName) {
        super("庫存不足，無法建立訂單：" + productName);
    }
}
