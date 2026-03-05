package com.coremall.sharedkernel.response;

import java.util.List;

public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code.name(), message, List.of()));
    }

    public static <T> ApiResponse<T> validationError(List<String> details) {
        return new ApiResponse<>(false, null,
                new ApiError(ErrorCode.VALIDATION_FAILED.name(), "請求參數驗證失敗", details));
    }
}
