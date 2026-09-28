package br.uesc.login.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginRateLimitFilter extends OncePerRequestFilter {
    private final LoginRateLimiter limiter;
    private final SecurityErrorWriter errors;

    public LoginRateLimitFilter(LoginRateLimiter limiter, SecurityErrorWriter errors) {
        this.limiter = limiter;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/auth/login".equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Não confiar em X-Forwarded-For enviado diretamente pelo cliente.
        long retryAfter = limiter.retryAfter(request.getRemoteAddr());
        if (retryAfter > 0) {
            response.setHeader("Retry-After", Long.toString(retryAfter));
            errors.write(response, 429, "Muitas tentativas. Aguarde um minuto e tente novamente.");
            return;
        }
        chain.doFilter(request, response);
    }
}
