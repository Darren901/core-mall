package com.coremall.gateway.util;

import com.coremall.gateway.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Component
public class JwtUtils {

    private final JwtProperties properties;

    public JwtUtils(JwtProperties properties) {
        this.properties = properties;
    }

    /**
     * token 中萃取 userId（subject claim）。
     *
     * @throws JwtException token 無效、過期或簽名不符
     */
    public String extractUserId(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }
}
