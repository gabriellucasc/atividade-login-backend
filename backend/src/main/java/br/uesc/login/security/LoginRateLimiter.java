package br.uesc.login.security;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private static final int MAX_IPS = 10_000;
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    private final int limit;
    private final long windowMillis;
    private record Window(long endsAt, int count) {}

    public LoginRateLimiter(Clock clock,
            @Value("${app.login-rate-limit.attempts:10}") int limit,
            @Value("${app.login-rate-limit.window-seconds:60}") long seconds) {
        if (limit < 1 || limit > 100 || seconds < 1 || seconds > 3600) {
            throw new IllegalArgumentException("Configuração de rate limit inválida.");
        }
        this.clock = clock;
        this.limit = limit;
        this.windowMillis = seconds * 1000;
    }

    // Zero permite a tentativa; valor positivo informa Retry-After em segundos.
    public synchronized long retryAfter(String ip) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> entry.getValue().endsAt() <= now);
        Window current = windows.get(ip);
        if (current == null) {
            // Limite de memória: falha fechada quando não cabe um novo IP.
            if (windows.size() >= MAX_IPS) return Math.max(1, windowMillis / 1000);
            windows.put(ip, new Window(now + windowMillis, 1));
            return 0;
        }
        if (current.count() >= limit) {
            return Math.max(1, (current.endsAt() - now + 999) / 1000);
        }
        windows.put(ip, new Window(current.endsAt(), current.count() + 1));
        return 0;
    }
}
