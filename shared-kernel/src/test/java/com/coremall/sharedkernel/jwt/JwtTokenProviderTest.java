package com.coremall.sharedkernel.jwt;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenProvider - JWT 簽發與解析")
class JwtTokenProviderTest {

    private static final String SECRET = "dGVzdC1qd3Qtc2VjcmV0LWtleS1tdXN0LWJlLWF0LWxlYXN0LTMy";

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(SECRET, Duration.ofHours(1));
        tokenProvider = new JwtTokenProvider(props);
    }

    @Test
    @DisplayName("簽發 token 後可正確解析出 userId")
    void shouldGenerateAndExtractUserId() {
        String userId = "user-uuid-123";
        String token = tokenProvider.generateToken(userId);
        assertThat(tokenProvider.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("token 字串無效時拋出 JwtException")
    void shouldThrowJwtExceptionWhenTokenIsInvalid() {
        assertThatThrownBy(() -> tokenProvider.extractUserId("this.is.invalid"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token 過期後拋出 JwtException")
    void shouldThrowJwtExceptionWhenTokenIsExpired() throws InterruptedException {
        JwtProperties shortLived = new JwtProperties(SECRET, Duration.ofMillis(1));
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(shortLived);
        String token = shortLivedProvider.generateToken("any-user");

        Thread.sleep(10);

        assertThatThrownBy(() -> shortLivedProvider.extractUserId(token))
                .isInstanceOf(JwtException.class);
    }
}
