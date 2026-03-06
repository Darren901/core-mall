package com.coremall.order.exception;

public class LockConflictException extends RuntimeException {
    public LockConflictException(String resource) {
        super("Failed to acquire lock for: " + resource);
    }
}
