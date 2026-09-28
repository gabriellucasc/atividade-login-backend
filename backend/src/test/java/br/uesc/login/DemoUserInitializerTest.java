package br.uesc.login;

import br.uesc.login.config.DemoUserInitializer;
import br.uesc.login.entity.AppUser;
import br.uesc.login.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoUserInitializerTest {
    @Test
    void demoIsHashedAndExistingUserIsNotOverwritten() {
        UserRepository users = mock(UserRepository.class);
        var encoder = new BCryptPasswordEncoder(12);
        var properties = new DemoUserInitializer.DemoProperties("Aluno@example.com", "Senha-academica-teste!");
        var initializer = new DemoUserInitializer(users, encoder, properties);
        initializer.run(null);
        var captured = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captured.capture());
        assertEquals("aluno@example.com", captured.getValue().getEmail());
        assertNotEquals(properties.password(), captured.getValue().getPasswordHash());
        assertTrue(encoder.matches(properties.password(), captured.getValue().getPasswordHash()));
        when(users.findByEmail("aluno@example.com")).thenReturn(Optional.of(captured.getValue()));
        initializer.run(null);
        verify(users, times(1)).save(any());
    }

    @Test
    void oversizedDemoPasswordIsRejectedWithoutLeakingValue() {
        String password = "á".repeat(37);
        var error = assertThrows(IllegalStateException.class,
                () -> new DemoUserInitializer(mock(UserRepository.class), new BCryptPasswordEncoder(12),
                        new DemoUserInitializer.DemoProperties("aluno@example.com", password)));
        assertFalse(error.getMessage().contains(password));
    }
}
