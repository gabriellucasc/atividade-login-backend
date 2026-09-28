package br.uesc.login.service;

import br.uesc.login.dto.*;
import br.uesc.login.entity.AppUser;
import br.uesc.login.repository.UserRepository;
import br.uesc.login.security.JwtService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final String dummyHash;

    public AuthenticationService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        // Mantém uma comparação BCrypt mesmo quando o e-mail não existe.
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(LoginRequest request) {
        AppUser user = users.findByEmail(request.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean matches = encoder.matches(request.password(), hash);
        if (user == null || !matches || !user.isEnabled()) {
            throw new BadCredentialsException("Email ou senha inválidos.");
        }
        return new LoginResponse(jwt.generate(user.getId()), "Bearer", jwt.expiresIn());
    }
}
