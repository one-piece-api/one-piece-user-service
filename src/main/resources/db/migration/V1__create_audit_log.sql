-- Audit trail for mutating admin actions (§13 of application-user-identity-management.md),
-- starting with UF-IDU-01 (invite user) - see docs/adr/0001-audit-log-persistence.md.
-- Write-only from the application's perspective: no endpoint reads this table back.
CREATE TABLE audit_log
(
    id             BIGSERIAL PRIMARY KEY,
    action         VARCHAR(64)  NOT NULL,
    actor_user_id  UUID         NOT NULL,
    actor_email    VARCHAR(255) NOT NULL,
    target_user_id UUID         NOT NULL,
    target_email   VARCHAR(255) NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_audit_log_target_user_id ON audit_log (target_user_id);
