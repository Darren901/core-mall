package com.coremall.gateway.util;

import com.coremall.gateway.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtUtils - JWT 工具類")
class JwtUtilsTest {

    private JwtUtils jwtUtils;
    private String secret;

    @BeforeEach
    void setUp() {
        // HS256 需至少 32 bytes
        secret = Base64.getEncoder()
                .encodeToString("test-jwt-secret-key-must-be-at-least-32".getBytes());
        JwtProperties props = new JwtProperties(secret, Duration.ofHours(1));
        jwtUtils = new JwtUtils(props);
    }

    @Test
    @DisplayName("有效 token 能成功萃取 userId")
    void shouldExtractUserIdFromValidToken() {
        String token = buildToken("user-uuid-123", new Date(System.currentTimeMillis() + 3_600_000));

        assertThat(jwtUtils.extractUserId(token)).isEqualTo("user-uuid-123");
    }

    @Test
    @DisplayName("過期 token 拋出 JwtException")
    void shouldThrowWhenTokenIsExpired() {
        String expiredToken = buildToken("user-uuid-123", new Date(System.currentTimeMillis() - 1_000));

        assertThatThrownBy(() -> jwtUtils.extractUserId(expiredToken))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    @DisplayName("格式錯誤的 token 拋出 JwtException")
    void shouldThrowWhenTokenIsMalformed() {
        assertThatThrownBy(() -> jwtUtils.extractUserId("not.a.valid.jwt"))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    @DisplayName("不同 secret 簽名的 token 拋出 JwtException")
    void shouldThrowWhenTokenSignedWithDifferentSecret() {
        String wrongSecret = Base64.getEncoder()
                .encodeToString("wrong-secret-key-must-be-at-least-32b".getBytes());
        String tokenWithWrongSecret = Jwts.builder()
                .subject("user-uuid-123")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(wrongSecret)))
                .compact();

        assertThatThrownBy(() -> jwtUtils.extractUserId(tokenWithWrongSecret))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    private String buildToken(String userId, Date expiration) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        return Jwts.builder()
                .subject(userId)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }
}
