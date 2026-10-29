# Decisões de arquitetura (ADRs)

Cada ADR registra **uma** decisão difícil de reverter, com contexto, alternativas e
consequências. ADR aceito não se edita: uma decisão nova o substitui.

| ADR | Título | Status | Data |
|---|---|---|---|
| [ADR-0001](0001-postgresql-como-banco-unico.md) | Usar PostgreSQL como banco único da aplicação, no lugar do Oracle | Aceito | 2026-10-28 |
| [ADR-0002](0002-java-25-lts.md) | Fixar Java 25 LTS como versão única do projeto e da CI | Aceito | 2026-10-28 |
| [ADR-0003](0003-spring-boot-4-como-framework.md) | Construir a API sobre Spring Boot 4.1.x, com starters modulares e Maven Wrapper | Aceito | 2026-10-28 |
| [ADR-0004](0004-erros-rfc-9457-com-type-urn.md) | Padronizar toda resposta de erro em RFC 9457 com `type` URN estável e textos pt-BR | Aceito | 2026-10-28 |
| [ADR-0005](0005-negacao-por-padrao-antes-do-roteamento.md) | Negar por padrão toda rota não pública antes do roteamento, respondendo 401 até para rota inexistente | Aceito | 2026-10-28 |
| [ADR-0006](0006-jwt-curto-com-renovacao-opaca-rotacionada.md) | Autenticar com JWT HS256 de 15 minutos e renovação opaca rotacionada por sessão | Aceito | 2026-10-29 |
| [ADR-0007](0007-argon2id-para-hash-de-senha.md) | Guardar senhas com Argon2id (m=19 MiB, t=2, p=1) via Spring Security e Bouncy Castle | Aceito | 2026-10-29 |
| [ADR-0008](0008-spring-data-jpa-com-schema-do-flyway.md) | Persistir com Spring Data JPA sobre o schema do Flyway, só validado pelo Hibernate | Aceito | 2026-10-29 |
