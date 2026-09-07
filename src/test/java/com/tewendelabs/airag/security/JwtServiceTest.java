package com.tewendelabs.airag.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tewendelabs.airag.config.JwtProperties;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

    private static final String TEST_SECRET = "test-secret-key-at-least-32-characters-long";

    private final JwtService jwtService = new JwtService(new JwtProperties(TEST_SECRET, 60));

    @Test
    void generateThenValidate_returnsSameUserId() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateToken(userId);
        UUID extracted = jwtService.validateAndExtractUserId(token);

        assertThat(extracted).isEqualTo(userId);
    }

    @Test
    void validate_rejectsTamperedToken() {
        String token = jwtService.generateToken(UUID.randomUUID());
        // On altere un caractere au milieu du token (partie header/payload), pas le dernier
        // caractere : dans un JWT HS256, la signature encodee en base64url fait 43 caracteres
        // pour 32 octets, donc son tout dernier caractere ne porte que 4 bits utiles sur 6 (les 2
        // bits restants sont ignores au decodage) - alterer precisement CE caractere a environ une
        // chance sur deux de ne toucher qu'un bit ignore et de laisser la signature decodee
        // inchangee, rendant ce test intermittent. Un caractere du milieu invalide toujours la
        // signature de maniere deterministe.
        int middle = token.length() / 2;
        String tampered = token.substring(0, middle)
                + (token.charAt(middle) == 'a' ? 'b' : 'a')
                + token.substring(middle + 1);

        assertThatThrownBy(() -> jwtService.validateAndExtractUserId(tampered))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void validate_rejectsExpiredToken() {
        var key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(past.minusSeconds(60)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtService.validateAndExtractUserId(expiredToken))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void validate_rejectsMalformedToken() {
        assertThatThrownBy(() -> jwtService.validateAndExtractUserId("not-a-jwt"))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void constructor_failsFastWhenSecretTooShort() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", 60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
