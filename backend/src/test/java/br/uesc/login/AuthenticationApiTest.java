package br.uesc.login;

import br.uesc.login.config.*;
import br.uesc.login.controller.*;
import br.uesc.login.entity.AppUser;
import br.uesc.login.exception.GlobalExceptionHandler;
import br.uesc.login.repository.UserRepository;
import br.uesc.login.security.*;
import br.uesc.login.service.AuthenticationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, ProfileController.class},
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@Import({SecurityConfig.class, JwtService.class, AuthenticationService.class,
        SecurityErrorWriter.class, LoginRateLimiter.class, GlobalExceptionHandler.class})
class AuthenticationApiTest {
    private static final byte[] KEY = new byte[32];
    private static final AtomicInteger NEXT_IP = new AtomicInteger();
    private static final String PASSWORD = "Senha-academica-teste!";
    private static final String HASH = new BCryptPasswordEncoder(12).encode(PASSWORD);
    static { new SecureRandom().nextBytes(KEY); }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> Base64.getEncoder().encodeToString(KEY));
        registry.add("app.jwt.ttl-seconds", () -> 900);
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean UserRepository users;
    private AppUser user;
    private String ip;

    @BeforeEach
    void setup() {
        user = new AppUser("aluno@example.com", HASH);
        ReflectionTestUtils.setField(user, "id", 1L);
        when(users.findByEmail("aluno@example.com")).thenReturn(Optional.of(user));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        ip = "192.0.2." + NEXT_IP.incrementAndGet();
    }

    private MockHttpServletRequestBuilder login(String json) {
        return post("/auth/login").servletPath("/auth/login")
                .with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private MockHttpServletRequestBuilder profile() {
        return get("/api/profile").servletPath("/api/profile");
    }

    private String validLogin() {
        return "{\"email\":\"aluno@example.com\",\"password\":\"" + PASSWORD + "\"}";
    }

    @Test
    void correctLoginReturnsJwt() throws Exception {
        mvc.perform(login(validLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void incorrectPasswordReturnsGeneric401() throws Exception {
        mvc.perform(login("{\"email\":\"aluno@example.com\",\"password\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou senha inválidos."));
    }

    @Test
    void nonexistentUserReturnsSameGeneric401() throws Exception {
        mvc.perform(login("{\"email\":\"ausente@example.com\",\"password\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou senha inválidos."));
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        ReflectionTestUtils.setField(user, "enabled", false);
        mvc.perform(login(validLogin())).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidPayloadReturns400BeforeRepository() throws Exception {
        mvc.perform(login("{\"email\":\"invalido\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(users, never()).findByEmail(anyString());
    }

    @Test
    void missingFieldsReturn400() throws Exception {
        mvc.perform(login("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(login("{")).andExpect(status().isBadRequest());
    }

    @Test
    void utf8PasswordBeyondBcryptLimitReturns400() throws Exception {
        mvc.perform(login("{\"email\":\"aluno@example.com\",\"password\":\"" + "á".repeat(37) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mvc.perform(profile()).andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenReturnsProfileWithoutHash() throws Exception {
        mvc.perform(profile().header("Authorization", "Bearer " + jwt.generate(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("aluno@example.com"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void malformedTokenReturns401() throws Exception {
        mvc.perform(profile().header("Authorization", "Bearer invalid.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSignatureReturns401() throws Exception {
        byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);
        String token = Jwts.builder().issuer("atividade5").subject("1")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(otherKey), Jwts.SIG.HS256).compact();
        mvc.perform(profile().header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        String token = Jwts.builder().issuer("atividade5").subject("1")
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(KEY), Jwts.SIG.HS256).compact();
        mvc.perform(profile().header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutExpirationReturns401() throws Exception {
        String token = Jwts.builder().issuer("atividade5").subject("1").issuedAt(new Date())
                .signWith(Keys.hmacShaKeyFor(KEY), Jwts.SIG.HS256).compact();
        mvc.perform(profile().header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledUserCannotUsePreviouslyIssuedToken() throws Exception {
        String token = jwt.generate(1L);
        ReflectionTestUtils.setField(user, "enabled", false);
        mvc.perform(profile().header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserWithoutRoleGets403() throws Exception {
        ReflectionTestUtils.setField(user, "role", "OTHER");
        mvc.perform(profile().header("Authorization", "Bearer " + jwt.generate(1L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void eleventhAttemptIsLimitedEvenWithSpoofedForwardedHeader() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(login("{}")).andExpect(status().isBadRequest());
        }
        mvc.perform(login("{}").header("X-Forwarded-For", "203.0.113.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void trustedOriginPreflightIsAllowed() throws Exception {
        mvc.perform(options("/api/profile").header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    void untrustedOriginIsRejected() throws Exception {
        mvc.perform(options("/api/profile").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void databaseFailureDoesNotExposeInternalDetails() throws Exception {
        when(users.findByEmail(anyString())).thenThrow(new IllegalStateException("sensitive internal detail"));
        mvc.perform(login(validLogin())).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Não foi possível processar a solicitação."))
                .andExpect(content().string(not(containsString("sensitive"))));
    }
}
