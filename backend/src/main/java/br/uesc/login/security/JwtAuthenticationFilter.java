package br.uesc.login.security;

import br.uesc.login.entity.AppUser;
import br.uesc.login.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserRepository users;
    private final SecurityErrorWriter errors;

    public JwtAuthenticationFilter(JwtService jwt, UserRepository users, SecurityErrorWriter errors) {
        this.jwt = jwt;
        this.users = users;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && "/auth/login".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.regionMatches(true, 0, "Bearer ", 0, 7) || header.length() > 4096) {
                    throw new JwtException("Bearer inválido.");
                }
                Long id = jwt.validateAndGetUserId(header.substring(7));
                AppUser user = users.findById(id).filter(AppUser::isEnabled)
                        .orElseThrow(() -> new JwtException("Usuário indisponível."));
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
                var authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                errors.write(response, 401, "Token inválido ou expirado.");
                return;
            } catch (Exception exception) {
                SecurityContextHolder.clearContext();
                errors.write(response, 500, "Não foi possível processar a solicitação.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
