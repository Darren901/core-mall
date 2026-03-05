package com.coremall.user.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("Email 已被使用：" + email);
    }
}
