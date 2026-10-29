-- Identidade: usuários, sessões, tokens de e-mail e eventos de segurança.
-- Os ids (UUID v7) e os instantes vêm da aplicação. Excluir um usuário apaga, em cascata,
-- tudo o que é dele.

CREATE TABLE users (
    id                uuid         PRIMARY KEY,
    full_name         varchar(100) NOT NULL,
    -- Já normalizado pela aplicação (sem espaços nas pontas e em minúsculas).
    email             varchar(254) NOT NULL,
    password_hash     varchar(255) NOT NULL,
    email_verified_at timestamptz,
    failed_logins     integer      NOT NULL DEFAULT 0,
    locked_until      timestamptz,
    last_login_at     timestamptz,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    version           bigint       NOT NULL,
    CONSTRAINT users_email_uk UNIQUE (email),
    CONSTRAINT users_full_name_ck CHECK (btrim(full_name) <> ''),
    -- Sem maiúsculas ASCII em vez de email = lower(email): o lower() do banco depende da collation
    -- e poderia recusar um endereço que a aplicação já normalizou.
    CONSTRAINT users_email_ck CHECK (email <> '' AND email = btrim(email) AND email !~ '[A-Z]'),
    CONSTRAINT users_failed_logins_ck CHECK (failed_logins >= 0)
);

CREATE TABLE sessions (
    id            uuid        PRIMARY KEY,
    user_id       uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- SHA-256 (hex) do segredo da credencial de renovação; o segredo nunca é gravado.
    refresh_hash  varchar(64) NOT NULL,
    created_at    timestamptz NOT NULL,
    last_used_at  timestamptz NOT NULL,
    expires_at    timestamptz NOT NULL,
    revoked_at    timestamptz,
    revoke_reason varchar(30),
    device        varchar(120),
    origin        varchar(45),
    CONSTRAINT sessions_refresh_hash_uk UNIQUE (refresh_hash),
    CONSTRAINT sessions_refresh_hash_ck CHECK (refresh_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT sessions_revoke_reason_ck CHECK (
        revoke_reason IN ('LOGOUT', 'REVOKED_BY_USER', 'PASSWORD_CHANGED', 'PASSWORD_RESET', 'REFRESH_REUSE')),
    -- Revogada sempre com motivo, e motivo só em sessão revogada.
    CONSTRAINT sessions_revocation_ck CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL)),
    CONSTRAINT sessions_expiry_ck CHECK (expires_at > created_at)
);

-- Índice completo, não parcial: a exclusão em cascata também precisa achar as sessões revogadas.
CREATE INDEX sessions_user_id_idx ON sessions (user_id);

CREATE TABLE email_tokens (
    id         uuid         PRIMARY KEY,
    user_id    uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    varchar(20)  NOT NULL,
    -- SHA-256 (hex) do token enviado por e-mail.
    token_hash varchar(64)  NOT NULL,
    new_email  varchar(254),
    expires_at timestamptz  NOT NULL,
    used_at    timestamptz,
    created_at timestamptz  NOT NULL,
    CONSTRAINT email_tokens_token_hash_uk UNIQUE (token_hash),
    CONSTRAINT email_tokens_token_hash_ck CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT email_tokens_purpose_ck CHECK (purpose IN ('EMAIL_VERIFICATION', 'EMAIL_CHANGE', 'PASSWORD_RESET')),
    -- O novo endereço existe só (e sempre) na troca de e-mail.
    CONSTRAINT email_tokens_new_email_ck CHECK ((purpose = 'EMAIL_CHANGE') = (new_email IS NOT NULL)),
    CONSTRAINT email_tokens_new_email_format_ck CHECK (
        new_email IS NULL OR (new_email <> '' AND new_email = btrim(new_email) AND new_email !~ '[A-Z]')),
    CONSTRAINT email_tokens_expiry_ck CHECK (expires_at > created_at)
);

-- Atende a FK (cascata) e a invalidação do token anterior da mesma finalidade.
CREATE INDEX email_tokens_user_id_purpose_idx ON email_tokens (user_id, purpose);

CREATE TABLE security_events (
    id          uuid         PRIMARY KEY,
    user_id     uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        varchar(40)  NOT NULL,
    occurred_at timestamptz  NOT NULL,
    device      varchar(120),
    origin      varchar(45),
    -- Sem FK: o evento sobrevive à limpeza das sessões expiradas.
    session_id  uuid,
    CONSTRAINT security_events_type_ck CHECK (type IN (
        'REGISTERED', 'EMAIL_VERIFIED',
        'LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'ACCOUNT_LOCKED',
        'PASSWORD_CHANGED', 'PASSWORD_RESET',
        'EMAIL_CHANGE_REQUESTED', 'EMAIL_CHANGED',
        'SESSION_REVOKED', 'REFRESH_REUSE'))
);

-- Atende a FK (cascata) e a listagem da atividade, da mais recente para a mais antiga.
CREATE INDEX security_events_user_id_occurred_at_idx ON security_events (user_id, occurred_at DESC);
