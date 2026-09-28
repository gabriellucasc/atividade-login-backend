package br.uesc.login.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(@NotBlank String secret, @Min(60) @Max(3600) long ttlSeconds) {
    @Override
    public String toString() { return "JwtProperties[redacted]"; }
}
