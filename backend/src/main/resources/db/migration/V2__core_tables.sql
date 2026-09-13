-- Core tenant-owned tables.
--
-- Every table below carries tenant_id, including the ones that could reach a
-- tenant by joining through a parent (messages -> conversations). That
-- redundancy is deliberate: V3 puts a row level security policy on each table,
-- and a policy can only filter on a column the row actually has.
--
-- Enums are stored as text with a CHECK rather than a Postgres enum type:
-- adding a value to a Postgres enum inside a transactional migration is awkward,
-- and a CHECK gives the same protection with a plain ALTER to change it.

CREATE TABLE tenants (
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    name       TEXT        NOT NULL,
    plan       TEXT        NOT NULL CHECK (plan IN ('FREE', 'PRO', 'ENTERPRISE')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE users (
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    tenant_id     UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    email         TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    role          TEXT        NOT NULL CHECK (role IN ('OWNER', 'AGENT')),
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,

    -- Global, not per-tenant. Login is email plus password with no tenant
    -- selector, so a per-tenant constraint would make "which account is this?"
    -- ambiguous the moment one address signed up twice.
    CONSTRAINT users_email_key UNIQUE (email)
);

CREATE TABLE conversations (
    id                   UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    tenant_id            UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    visitor_id           UUID        NOT NULL,
    status               TEXT        NOT NULL CHECK (status IN ('BOT_ACTIVE', 'AWAITING_HUMAN', 'HUMAN_ACTIVE', 'RESOLVED')),
    assigned_operator_id UUID REFERENCES users (id) ON DELETE SET NULL,
    escalation_reason    TEXT,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);

-- Serves the operator dashboard's main query: this tenant's conversations,
-- newest first, filtered by status so AWAITING_HUMAN sorts to the top.
CREATE INDEX conversations_tenant_status_updated_idx
    ON conversations (tenant_id, status, updated_at DESC);

CREATE TABLE messages (
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            TEXT        NOT NULL CHECK (role IN ('SYSTEM', 'USER', 'ASSISTANT', 'TOOL')),
    content         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL
);

-- Serves the transcript read: one conversation, in the order it happened.
CREATE INDEX messages_conversation_created_idx
    ON messages (conversation_id, created_at);

CREATE TABLE documents (
    id             UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    tenant_id      UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    filename       TEXT        NOT NULL,
    content_type   TEXT        NOT NULL,
    size_bytes     BIGINT      NOT NULL CHECK (size_bytes > 0),
    storage_path   TEXT        NOT NULL,
    status         TEXT        NOT NULL CHECK (status IN ('PENDING', 'INDEXING', 'INDEXED', 'FAILED')),
    attempts       INTEGER     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    failure_reason TEXT,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

-- Serves the indexing worker's claim query: this tenant's pending documents.
CREATE INDEX documents_tenant_status_idx
    ON documents (tenant_id, status);
