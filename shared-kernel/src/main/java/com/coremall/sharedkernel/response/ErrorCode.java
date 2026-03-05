package com.coremall.sharedkernel.response;

public enum ErrorCode {
    // 驗證 / 授權
    UNAUTHORIZED,
    FORBIDDEN,

    // 使用者
    USER_NOT_FOUND,
    USER_EMAIL_DUPLICATE,

    // 訂單
    ORDER_NOT_FOUND,
    ORDER_ALREADY_CANCELLED,
    ORDER_LOCK_CONFLICT,

    // 通用
    VALIDATION_FAILED,
    INTERNAL_ERROR
}
