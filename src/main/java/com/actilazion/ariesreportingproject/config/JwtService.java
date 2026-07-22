package com.actilazion.ariesreportingproject.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Date;
import java.util.function.Function;
import javax.crypto.SecretKey;

@Service
@RequiredArgsConstructor
public class JwtService {
    private static final String TOKEN_TYPE_CLAIM = "typ";

    private final JwtProperties jwtProperties;

    @PostConstruct
    void validateConfiguration() {
        getSigningKey();
        requireText(jwtProperties.getIssuer(), "jwt.issuer");
        requireText(jwtProperties.getAudience(), "jwt.audience");
        requireText(jwtProperties.getTokenType(), "jwt.token-type");
        if (jwtProperties.getClockSkewSeconds() < 0) {
            throw new IllegalStateException("jwt.clock-skew-seconds must not be negative");
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .clockSkewSeconds(jwtProperties.getClockSkewSeconds())
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        validateClaims(claims);
        return resolver.apply(claims);
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
    }

    private void validateClaims(Claims claims) {
        if (!jwtProperties.getIssuer().equals(claims.getIssuer())) {
            throw new IllegalArgumentException("JWT issuer is invalid");
        }
        if (!audienceMatches(claims.get(Claims.AUDIENCE))) {
            throw new IllegalArgumentException("JWT audience is invalid");
        }
        if (!jwtProperties.getTokenType().equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new IllegalArgumentException("JWT token type is invalid");
        }
        requireText(claims.getSubject(), "jwt.subject");
        if (claims.getExpiration() == null) {
            throw new IllegalArgumentException("JWT expiration is required");
        }
    }

    private boolean audienceMatches(Object audience) {
        if (audience instanceof String value) {
            return jwtProperties.getAudience().equals(value);
        }
        if (audience instanceof Collection<?> values) {
            return values.contains(jwtProperties.getAudience());
        }
        return false;
    }

    private void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
    }
}
