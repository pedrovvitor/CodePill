-- Runs once on first postgres container start (docker-entrypoint-initdb.d).
-- One database per owning service (ARCHITECTURE.md §3.2: services own their
-- data, no shared databases) plus Keycloak's own store.

CREATE DATABASE codepill_identity;
CREATE DATABASE codepill_catalog;
CREATE DATABASE keycloak;

-- Query-performance visibility (OBSERVABILITY.md §4.2)
\connect codepill_identity
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

\connect codepill_catalog
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
