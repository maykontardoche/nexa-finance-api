package br.com.fintech.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

    Optional<EmailToken> findByTokenHashAndPurpose(String tokenHash, EmailTokenPurpose purpose);

    /**
     * Marca o token como usado se, e só se, ele ainda está livre e no prazo. É atômico: de duas
     * requisições simultâneas com o mesmo token, só uma recebe 1.
     *
     * @return 1 se consumiu agora; 0 se já estava usado, expirado ou não existe
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailToken t set t.usedAt = :now where t.id = :id and t.usedAt is null and t.expiresAt > :now")
    int consume(UUID id, Instant now);

    /** Invalida os tokens ainda não usados da finalidade: token novo derruba os anteriores. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EmailToken t where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    int deleteUnused(UUID userId, EmailTokenPurpose purpose);
}
