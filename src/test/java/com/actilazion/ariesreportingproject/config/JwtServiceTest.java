package com.actilazion.ariesreportingproject.config;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private static final String SECRET =
            "dGVzdFNlY3JldEtleUZvckp3dFRlc3RpbmdQdXJwb3Nlc09ubHk=";
    private static final String LONG_SECRET = Base64.getEncoder().encodeToString(
            "0123456789012345678901234567890101234567890123456789012345678901"
                    .getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("isTokenValid: accepts transaction JWT contract")
    void isTokenValid_validContract_returnsTrue() {
        JwtService service = new JwtService(jwtProperties());
        var user = User.withUsername("user@aries.local")
                .password("n/a")
                .roles("USER")
                .build();

        boolean valid = service.isTokenValid(
                token("aries-transaction", "aries-transaction-api", "access"),
                user);

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("isTokenValid: accepts expiration within configured clock skew")
    void isTokenValid_expiredWithinClockSkew_returnsTrue() {
        JwtService service = new JwtService(jwtProperties());
        var user = User.withUsername("user@aries.local")
                .password("n/a")
                .roles("USER")
                .build();
        Instant now = Instant.now();

        boolean valid = service.isTokenValid(
                token("aries-transaction", "aries-transaction-api", "access",
                        now.minusSeconds(10)),
                user);

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("isTokenValid: rejects expiration outside configured clock skew")
    void isTokenValid_expiredOutsideClockSkew_rejects() {
        JwtService service = new JwtService(jwtProperties());
        var user = User.withUsername("user@aries.local")
                .password("n/a")
                .roles("USER")
                .build();
        Instant now = Instant.now();

        assertThatThrownBy(() -> service.isTokenValid(
                token("aries-transaction", "aries-transaction-api", "access",
                        now.minusSeconds(60)),
                user))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("extractUsername: rejects wrong issuer")
    void extractUsername_wrongIssuer_rejects() {
        JwtService service = new JwtService(jwtProperties());

        assertThatThrownBy(() -> service.extractUsername(
                token("wrong-issuer", "aries-transaction-api", "access")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT issuer is invalid");
    }

    @Test
    @DisplayName("extractUsername: rejects missing issuer")
    void extractUsername_missingIssuer_rejects() {
        JwtService service = new JwtService(jwtProperties());

        assertThatThrownBy(() -> service.extractUsername(tokenWithoutIssuer()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT issuer is invalid");
    }

    @Test
    @DisplayName("extractUsername: rejects wrong audience")
    void extractUsername_wrongAudience_rejects() {
        JwtService service = new JwtService(jwtProperties());

        assertThatThrownBy(() -> service.extractUsername(
                token("aries-transaction", "wrong-audience", "access")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT audience is invalid");
    }

    @Test
    @DisplayName("extractUsername: rejects missing audience")
    void extractUsername_missingAudience_rejects() {
        JwtService service = new JwtService(jwtProperties());

        assertThatThrownBy(() -> service.extractUsername(tokenWithoutAudience()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT audience is invalid");
    }

    @Test
    @DisplayName("extractUsername: rejects wrong token type")
    void extractUsername_wrongTokenType_rejects() {
        JwtService service = new JwtService(jwtProperties());

        assertThatThrownBy(() -> service.extractUsername(
                token("aries-transaction", "aries-transaction-api", "refresh")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT token type is invalid");
    }

    @Test
    @DisplayName("extractUsername: rejects future not-before")
    void extractUsername_futureNotBefore_rejects() {
        JwtService service = new JwtService(jwtProperties());

        assertThatThrownBy(() -> service.extractUsername(tokenWithFutureNotBefore()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("extractUsername: rejects unexpected signing algorithm")
    void extractUsername_unexpectedAlgorithm_rejects() {
        JwtService service = new JwtService(jwtProperties(LONG_SECRET));

        assertThatThrownBy(() -> service.extractUsername(tokenWithHs512()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT algorithm is invalid");
    }

    private JwtProperties jwtProperties() {
        return jwtProperties(SECRET);
    }

    private JwtProperties jwtProperties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setIssuer("aries-transaction");
        properties.setAudience("aries-transaction-api");
        properties.setTokenType("access");
        properties.setClockSkewSeconds(30);
        return properties;
    }

    private String token(String issuer, String audience, String tokenType) {
        Instant now = Instant.now();
        return token(issuer, audience, tokenType, now.plusSeconds(300));
    }

    private String token(String issuer, String audience, String tokenType, Instant expiration) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("user@aries.local")
                .issuer(issuer)
                .audience()
                .add(audience)
                .and()
                .claim("typ", tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), Jwts.SIG.HS256)
                .compact();
    }

    private String tokenWithoutIssuer() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("user@aries.local")
                .audience()
                .add("aries-transaction-api")
                .and()
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), Jwts.SIG.HS256)
                .compact();
    }

    private String tokenWithoutAudience() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("user@aries.local")
                .issuer("aries-transaction")
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), Jwts.SIG.HS256)
                .compact();
    }

    private String tokenWithFutureNotBefore() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("user@aries.local")
                .issuer("aries-transaction")
                .audience()
                .add("aries-transaction-api")
                .and()
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now.plusSeconds(120)))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), Jwts.SIG.HS256)
                .compact();
    }

    private String tokenWithHs512() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("user@aries.local")
                .issuer("aries-transaction")
                .audience()
                .add("aries-transaction-api")
                .and()
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(LONG_SECRET)), Jwts.SIG.HS512)
                .compact();
    }
}
