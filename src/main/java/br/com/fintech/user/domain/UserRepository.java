package br.com.fintech.user.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** {@code email} já normalizado: a busca é exata, como a unicidade no banco. */
    Optional<User> findByEmail(String email);
}
