package com.coremall.sharedkernel.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse - 統一回應格式")
class ApiResponseTest {

    @Test
    @DisplayName("success() 回傳 success=true，data 有值，error 為 null")
    void shouldCreateSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("hello");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("hello");
        assertThat(response.error()).isNull();
    }

    @Test
    @DisplayName("success() data 可為 null（204 場景）")
    void shouldCreateSuccessResponseWithNullData() {
        ApiResponse<Void> response = ApiResponse.success(null);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isNull();
    }

    @Test
    @DisplayName("error() 回傳 success=false，data 為 null，error 含 code 與 message")
    void shouldCreateErrorResponse() {
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.UNAUTHORIZED, "未授權");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.error().code()).isEqualTo("UNAUTHORIZED");
        assertThat(response.error().message()).isEqualTo("未授權");
        assertThat(response.error().details()).isEmpty();
    }

    @Test
    @DisplayName("validationError() 回傳 VALIDATION_FAILED code 並含 details")
    void shouldCreateValidationErrorResponse() {
        ApiResponse<Void> response = ApiResponse.validationError(
                java.util.List.of("email 格式錯誤", "name 不可為空"));

        assertThat(response.success()).isFalse();
        assertThat(response.error().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.error().details()).containsExactly("email 格式錯誤", "name 不可為空");
    }
}
