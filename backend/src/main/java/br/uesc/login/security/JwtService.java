package br.uesc.login.security;

import br.uesc.login.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String ISSUER = "atividade5";
    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        try {
            byte[] bytes = Decoders.BASE64.decode(properties.secret());
            if (bytes.length < 32) {
                throw new IllegalArgumentException();
            }
            this.key = Keys.hmacShaKeyFor(bytes);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("JWT_SECRET deve ser Base64 de pelo menos 32 bytes aleatórios.");
        }
    }

    public String generate(Long userId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.ttlSeconds())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Long validateAndGetUserId(String token) {
        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token);
        if (!"HS256".equals(parsed.getHeader().getAlgorithm())) {
            throw new JwtException("Algoritmo não permitido.");
        }
        Claims claims = parsed.getPayload();
        if (claims.getExpiration() == null || claims.getIssuedAt() == null) {
            throw new JwtException("Claims obrigatórias ausentes.");
        }
        return Long.valueOf(claims.getSubject());
    }

    public long expiresIn() { return properties.ttlSeconds(); }
}
