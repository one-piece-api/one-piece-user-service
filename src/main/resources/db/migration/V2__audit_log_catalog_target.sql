-- Role/permission catalog actions (ROLE_CREATED, ROLE_DELETED, PERMISSION_CREATED,
-- PERMISSION_ASSIGNED_TO_ROLE, PERMISSION_REVOKED_FROM_ROLE - see
-- docs/adr/0012-role-permission-catalog-management.md) target the catalog itself, not a
-- user account: target_user_id/target_email become optional, and target_label carries a
-- role name or permission key for these actions instead - kept as its own column rather
-- than reusing target_email, so a role name is never stored in a column meant for an
-- email address.
ALTER TABLE audit_log ALTER COLUMN target_user_id DROP NOT NULL;
ALTER TABLE audit_log ALTER COLUMN target_email DROP NOT NULL;
ALTER TABLE audit_log ADD COLUMN target_label VARCHAR(255);
