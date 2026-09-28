package br.uesc.login;

import br.uesc.login.security.LoginRateLimiter;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginRateLimiterTest {
    @Test
    void windowExpiresAndIpsAreIndependent() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1_000L);
        LoginRateLimiter limiter = new LoginRateLimiter(clock, 2, 60);
        assertEquals(0, limiter.retryAfter("ip-a"));
        assertEquals(0, limiter.retryAfter("ip-a"));
        assertEquals(60, limiter.retryAfter("ip-a"));
        assertEquals(0, limiter.retryAfter("ip-b"));
        when(clock.millis()).thenReturn(61_000L);
        assertEquals(0, limiter.retryAfter("ip-a"));
    }
}
