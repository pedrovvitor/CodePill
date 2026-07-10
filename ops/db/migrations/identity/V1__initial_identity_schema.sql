-- codepill-identity — initial schema
--
-- Owns account records only. Credentials, MFA, and roles live in the IdP
-- (Keycloak); this table maps the IdP subject to a platform user id
-- (SECURITY.md §2.1). Other services reference users.id as an opaque UUID —
-- no cross-service foreign keys.

CREATE EXTENSION IF NOT EXISTS citext;

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE users (
    id           uuid         NOT NULL DEFAULT gen_random_uuid(),
    idp_subject  varchar(255) NOT NULL,  -- JWT `sub` from the IdP
    email        citext       NOT NULL,
    display_name varchar(120) NOT NULL,
    status       varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED'))
);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Read paths:
--   token -> user resolution on every authenticated request (hottest lookup)
CREATE UNIQUE INDEX ux_users_idp_subject ON users (idp_subject);
--   login/linking and uniqueness enforcement (citext = case-insensitive)
CREATE UNIQUE INDEX ux_users_email ON users (email);
--   admin listings filtered by status, newest first
CREATE INDEX ix_users_status_created_at ON users (status, created_at DESC);
