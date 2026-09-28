package br.uesc.login;

import br.uesc.login.config.JwtProperties;
import br.uesc.login.security.JwtService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    @Test
    void tokenHasExpectedMinimalClaimsAndLifetime() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var service = new JwtService(new JwtProperties(Base64.getEncoder().encodeToString(key), 900),
                Clock.fixed(now, ZoneOffset.UTC));
        String token = service.generate(42L);
        Claims claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(key)).build()
                .parseSignedClaims(token).getPayload();
        assertEquals(42L, service.validateAndGetUserId(token));
        assertEquals("42", claims.getSubject());
        assertEquals(now.plusSeconds(900), claims.getExpiration().toInstant());
        assertEquals(Set.of("iss", "sub", "iat", "exp"), claims.keySet());
    }

    @Test
    void weakKeyIsRejectedWithoutEchoingSecret() {
        var error = assertThrows(IllegalStateException.class,
                () -> new JwtService(new JwtProperties("YWJj", 900), Clock.systemUTC()));
        assertFalse(error.getMessage().contains("YWJj"));
    }
}
