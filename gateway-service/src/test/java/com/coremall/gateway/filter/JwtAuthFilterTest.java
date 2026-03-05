package com.coremall.gateway.filter;

import com.coremall.gateway.util.JwtUtils;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthFilter - Gateway JWT 驗證 Filter")
class JwtAuthFilterTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private GatewayFilterChain chain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("auth 公開路徑不需 token，直接轉發")
    void shouldPassThroughPublicAuthPaths() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());

        jwtAuthFilter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("無 Authorization header 時回傳 401")
    void shouldReturn401WhenNoAuthorizationHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/agent/chat").build());

        jwtAuthFilter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Authorization header 非 Bearer 格式時回傳 401")
    void shouldReturn401WhenAuthHeaderIsNotBearer() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/agent/chat")
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                        .build());

        jwtAuthFilter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("token 無效時回傳 401")
    void shouldReturn401WhenTokenIsInvalid() {
        when(jwtUtils.extractUserId("invalid-token"))
                .thenThrow(new JwtException("invalid token"));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/agent/chat")
                        .header("Authorization", "Bearer invalid-token")
                        .build());

        jwtAuthFilter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("有效 token 時注入 X-User-Id header 並繼續轉發")
    void shouldInjectUserIdHeaderWhenTokenIsValid() {
        String validToken = "valid.jwt.token";
        String userId = "user-uuid-123";
        when(jwtUtils.extractUserId(validToken)).thenReturn(userId);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/agent/chat")
                        .header("Authorization", "Bearer " + validToken)
                        .build());

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        jwtAuthFilter.filter(exchange, chain).block();

        verify(chain).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Id"))
                .isEqualTo(userId);
    }
}
