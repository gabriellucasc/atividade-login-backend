package br.uesc.login.repository;

import br.uesc.login.entity.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    // Spring Data usa parâmetros; a entrada não é concatenada em SQL.
    Optional<AppUser> findByEmail(String email);
}
