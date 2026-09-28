package br.uesc.login.config;

import br.uesc.login.entity.AppUser;
import br.uesc.login.repository.UserRepository;
import jakarta.validation.constraints.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@EnableConfigurationProperties(DemoUserInitializer.DemoProperties.class)
public class DemoUserInitializer implements ApplicationRunner {
    @Validated
    @ConfigurationProperties("app.demo")
    public record DemoProperties(@NotBlank @Email @Size(max = 254) String email,
                                 String password) {
        @Override
        public String toString() { return "DemoProperties[redacted]"; }
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final DemoProperties properties;

    public DemoUserInitializer(UserRepository users, PasswordEncoder encoder, DemoProperties properties) {
        String password = properties.password();
        if (password == null || password.isBlank() || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("DEMO_USER_PASSWORD deve ter pelo menos 12 caracteres e no máximo 72 bytes UTF-8.");
        }
        this.users = users;
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmail(email).isEmpty()) {
            users.save(new AppUser(email, encoder.encode(properties.password())));
        }
    }
}
