package dev.onepieceapi.userservice.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A record of who did what to whom, for traceability only (§13 of
 * {@code application-user-identity-management.md}) - never read back by this application,
 * in particular never used to power the admin UI (see {@code AuditLogPort}). Keycloak has
 * no equivalent bookkeeping (its action tokens carry no "who invited whom"), so this is
 * genuinely application-owned data, unlike everything else in this domain.
 * <p>
 * {@code targetUserId}/{@code targetEmail} are set when the target is a user account
 * (every action through UF-IDU-13/14/15/16). {@code targetLabel} carries whatever extra
 * context a plain actor/target identity pair can't: for a role/permission catalog action
 * (no user account is the target at all - see
 * {@code docs/adr/0012-role-permission-catalog-management.md}) it's a role name or
 * permission key (or both, as {@code "role <- permission"}, for assigning/revoking a
 * permission on a role); for a role granted at invite time or via
 * {@code ROLE_ASSIGNED}/{@code ROLE_REVOKED}, it's the role name itself - the audit trail
 * otherwise couldn't say *which* role changed, only that some role did. Kept as its own
 * field rather than reusing {@code targetEmail}, so a role name is never stored in a
 * column meant for an email address.
 */
public record AuditEvent(AuditAction action, UUID actorUserId, String actorEmail, UUID targetUserId, String targetEmail,
		String targetLabel, Instant occurredAt) {
}
