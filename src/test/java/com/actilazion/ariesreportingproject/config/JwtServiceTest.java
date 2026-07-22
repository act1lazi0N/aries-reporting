package com.actilazion.ariesreportingproject.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private static final String SECRET =
            "dGVzdFNlY3JldEtleUZvckp3dFRlc3RpbmdQdXJwb3Nlc09ubHk=";

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
    @DisplayName("extractUsername: rejects wrong issuer")
    void extractUsername_wrongIssuer_rejects() {
        JwtService service = new JwtService(jwtProperties());

        assertThatThrownBy(() -> service.extractUsername(
                token("wrong-issuer", "aries-transaction-api", "access")))
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
    @DisplayName("extractUsername: rejects wrong token type")
    void extractUsername_wrongTokenType_rejects() {
        JwtService service = new JwtService(jwtProperties());

        assertThatThrownBy(() -> service.extractUsername(
                token("aries-transaction", "aries-transaction-api", "refresh")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT token type is invalid");
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer("aries-transaction");
        properties.setAudience("aries-transaction-api");
        properties.setTokenType("access");
        properties.setClockSkewSeconds(30);
        return properties;
    }

    private String token(String issuer, String audience, String tokenType) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("user@aries.local")
                .issuer(issuer)
                .audience()
                .add(audience)
                .and()
                .claim("typ", tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), Jwts.SIG.HS256)
                .compact();
    }
}
