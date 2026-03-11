package com.coremall.order.controller;

import com.coremall.order.exception.InsufficientStockException;
import com.coremall.order.exception.LockConflictException;
import com.coremall.order.exception.OrderNotFoundException;
import com.coremall.order.exception.ServiceUnavailableException;
import com.coremall.sharedkernel.response.ApiResponse;
import com.coremall.sharedkernel.response.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleOrderNotFound(OrderNotFoundException ex) {
        return ApiResponse.error(ErrorCode.ORDER_NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(LockConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleLockConflict(LockConflictException ex) {
        return ApiResponse.error(ErrorCode.ORDER_LOCK_CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiResponse<Void> handleInsufficientStock(InsufficientStockException ex) {
        return ApiResponse.error(ErrorCode.INSUFFICIENT_STOCK, ex.getMessage());
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handleServiceUnavailable(ServiceUnavailableException ex) {
        return ApiResponse.error(ErrorCode.INVENTORY_SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return ApiResponse.validationError(details);
    }
}
