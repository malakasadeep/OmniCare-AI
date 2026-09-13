-- Row level security: the backstop for tenant isolation.
--
-- The application already filters by tenant_id. This exists for the query where
-- somebody forgets. With these policies in place a bare
--     SELECT * FROM conversations
-- returns only the current tenant's rows, because Postgres rewrites it before it
-- ever runs.
--
-- Two details that silently turn all of this into decoration:
--
-- 1. FORCE. ENABLE ROW LEVEL SECURITY does not apply to the table's *owner*, and
--    the application connects as the owner of these tables. Without FORCE the
--    policies below would be enabled, visible in \d, and never once consulted.
--
-- 2. The `true` in current_setting('app.tenant_id', true). Without it,
--    current_setting raises when the setting is missing, and every query outside
--    a tenant-scoped transaction becomes an error instead of an empty result.
--    With it the setting reads as NULL, `tenant_id = NULL` is NULL rather than
--    true, and the row is not returned. Unset context therefore fails closed.
--
-- Scope: the tenant's *content*. tenants and users are deliberately excluded --
-- login resolves a user by a globally unique address before any tenant is known,
-- so a tenant-scoped policy on users would make logging in impossible. Those two
-- tables stay guarded by application-level checks; see docs/backlog.md for the
-- separate database role that would close the gap properly.

CREATE OR REPLACE FUNCTION current_tenant_id() RETURNS UUID
    LANGUAGE sql
    STABLE
AS
$$
SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid
$$;

COMMENT ON FUNCTION current_tenant_id() IS
    'The tenant the current transaction is scoped to, or NULL when unscoped. '
        'Set per transaction with set_config(''app.tenant_id'', ..., true).';

ALTER TABLE conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversations FORCE ROW LEVEL SECURITY;
CREATE POLICY conversations_tenant_isolation ON conversations
    FOR ALL
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages FORCE ROW LEVEL SECURITY;
CREATE POLICY messages_tenant_isolation ON messages
    FOR ALL
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents FORCE ROW LEVEL SECURITY;
CREATE POLICY documents_tenant_isolation ON documents
    FOR ALL
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
