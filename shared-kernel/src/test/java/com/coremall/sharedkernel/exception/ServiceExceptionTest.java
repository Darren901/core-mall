package com.coremall.sharedkernel.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ServiceException - 服務呼叫例外分類")
class ServiceExceptionTest {

    @Nested
    @DisplayName("ServiceTransientException - 暫時性錯誤（5xx / IOException）")
    class ServiceTransientExceptionTest {

        @Test
        @DisplayName("以訊息建立，getMessage() 回傳該訊息")
        void shouldCreateWithMessage() {
            var ex = new ServiceTransientException("服務暫時不可用");

            assertThat(ex.getMessage()).isEqualTo("服務暫時不可用");
            assertThat(ex.getCause()).isNull();
        }

        @Test
        @DisplayName("以訊息與原因建立，getCause() 回傳原因")
        void shouldCreateWithMessageAndCause() {
            var cause = new RuntimeException("upstream error");
            var ex = new ServiceTransientException("服務暫時不可用", cause);

            assertThat(ex.getMessage()).isEqualTo("服務暫時不可用");
            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test
        @DisplayName("是 RuntimeException 的子類別")
        void shouldExtendRuntimeException() {
            assertThat(new ServiceTransientException("msg"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("不是 ServiceBusinessException")
        void shouldNotBeServiceBusinessException() {
            assertThat(new ServiceTransientException("msg"))
                    .isNotInstanceOf(ServiceBusinessException.class);
        }
    }

    @Nested
    @DisplayName("ServiceBusinessException - 業務規則拒絕（4xx）")
    class ServiceBusinessExceptionTest {

        @Test
        @DisplayName("以訊息建立，getMessage() 回傳該訊息")
        void shouldCreateWithMessage() {
            var ex = new ServiceBusinessException("訂單不存在");

            assertThat(ex.getMessage()).isEqualTo("訂單不存在");
            assertThat(ex.getCause()).isNull();
        }

        @Test
        @DisplayName("以訊息與原因建立，getCause() 回傳原因")
        void shouldCreateWithMessageAndCause() {
            var cause = new RuntimeException("downstream error");
            var ex = new ServiceBusinessException("訂單不存在", cause);

            assertThat(ex.getMessage()).isEqualTo("訂單不存在");
            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test
        @DisplayName("是 RuntimeException 的子類別")
        void shouldExtendRuntimeException() {
            assertThat(new ServiceBusinessException("msg"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("不是 ServiceTransientException")
        void shouldNotBeServiceTransientException() {
            assertThat(new ServiceBusinessException("msg"))
                    .isNotInstanceOf(ServiceTransientException.class);
        }
    }
}
