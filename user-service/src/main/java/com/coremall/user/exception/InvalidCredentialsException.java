package com.coremall.user.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("帳號或密碼錯誤");
    }
}
