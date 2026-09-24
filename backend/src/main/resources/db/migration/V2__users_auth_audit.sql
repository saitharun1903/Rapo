CREATE TABLE users (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    email          citext       NOT NULL,
    phone          varchar(20),
    password_hash  varchar(100) NOT NULL,
    full_name      varchar(120) NOT NULL,
    role           varchar(16)  NOT NULL,
    status         varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at  timestamptz,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    version        bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ux_users_email UNIQUE (email),
    CONSTRAINT ux_users_phone UNIQUE (phone),
    CONSTRAINT ck_users_role   CHECK (role IN ('PASSENGER', 'DRIVER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX ix_users_role_status ON users (role, status);

CREATE TABLE refresh_tokens (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash      varchar(64) NOT NULL,
    family_id       uuid        NOT NULL,
    expires_at      timestamptz NOT NULL,
    revoked_at      timestamptz,
    replaced_by_id  uuid        REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_user   ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE audit_logs (
    id             bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id  uuid        REFERENCES users (id) ON DELETE SET NULL,
    action         varchar(64) NOT NULL,
    entity_type    varchar(40) NOT NULL,
    entity_id      uuid,
    details        jsonb,
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_logs_entity  ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_logs_created ON audit_logs (created_at DESC);
CREATE INDEX ix_audit_logs_actor   ON audit_logs (actor_user_id, created_at DESC);
