package br.com.fintech.auth.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {
}
