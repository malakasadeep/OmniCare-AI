-- Runs once, as the superuser, when the data directory is first created.
--
-- Its whole job is to make sure the application does NOT connect as a superuser,
-- because a superuser bypasses row level security unconditionally — ENABLE and
-- even FORCE are ignored for them. Connect as the POSTGRES_USER and every policy
-- in V3__rls.sql is silently decorative.
--
-- The extensions are created here rather than left to V1 alone because creating
-- an extension needs superuser rights. V1 still declares them with
-- IF NOT EXISTS, which a non-superuser may run once they already exist, so the
-- migration stays honest about what the schema requires.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE ROLE omnicare_app
    LOGIN PASSWORD 'omnicare_app_dev'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

-- Flyway runs as this role and needs to create tables. Owning the schema is
-- enough; in Postgres 15 and later `public` is no longer writable by everyone.
ALTER SCHEMA public OWNER TO omnicare_app;
GRANT ALL ON SCHEMA public TO omnicare_app;
