-- Runs once on first postgres container start (docker-entrypoint-initdb.d).
-- One database per owning service (ARCHITECTURE.md §3.2) plus Keycloak's own
-- store, each owned by its own least-privilege login role. Passwords are
-- local-dev fixtures; real environments manage their own roles.
-- Existing volumes never re-run this script — recreate or apply manually.

CREATE ROLE codepill_identity_svc LOGIN PASSWORD 'codepill-local';
CREATE ROLE codepill_catalog_svc  LOGIN PASSWORD 'codepill-local';
CREATE ROLE keycloak_svc          LOGIN PASSWORD 'codepill-local';

CREATE DATABASE codepill_identity OWNER codepill_identity_svc;
CREATE DATABASE codepill_catalog  OWNER codepill_catalog_svc;
CREATE DATABASE keycloak          OWNER keycloak_svc;

-- Deny cross-database connects (PUBLIC would otherwise allow any role in)
REVOKE CONNECT ON DATABASE codepill_identity FROM PUBLIC;
REVOKE CONNECT ON DATABASE codepill_catalog  FROM PUBLIC;
REVOKE CONNECT ON DATABASE keycloak          FROM PUBLIC;
GRANT  CONNECT ON DATABASE codepill_identity TO codepill_identity_svc;
GRANT  CONNECT ON DATABASE codepill_catalog  TO codepill_catalog_svc;
GRANT  CONNECT ON DATABASE keycloak          TO keycloak_svc;

-- Query-performance visibility (OBSERVABILITY.md §4.2)
\connect codepill_identity
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

\connect codepill_catalog
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
