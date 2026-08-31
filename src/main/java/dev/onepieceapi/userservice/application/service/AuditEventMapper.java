package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.domain.AuditAction;
import dev.onepieceapi.userservice.domain.AuditEvent;
import dev.onepieceapi.userservice.domain.User;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds audit events (§13 of {@code application-user-identity-management.md}) from the
 * domain objects a use-case service already has on hand, keeping {@link AuditEvent}'s
 * field order/shape out of those services.
 */
@UtilityClass
class AuditEventMapper {

	AuditEvent userInvited(User actor, User invited, Instant occurredAt) {
		return ofUser(AuditAction.USER_INVITED, actor, invited, occurredAt);
	}

	AuditEvent invitationResent(User actor, User target, Instant occurredAt) {
		return ofUser(AuditAction.INVITATION_RESENT, actor, target, occurredAt);
	}

	AuditEvent roleAssigned(User actor, User target, Instant occurredAt) {
		return ofUser(AuditAction.ROLE_ASSIGNED, actor, target, occurredAt);
	}

	AuditEvent roleRevoked(User actor, User target, Instant occurredAt) {
		return ofUser(AuditAction.ROLE_REVOKED, actor, target, occurredAt);
	}

	AuditEvent accessRevoked(User actor, User target, Instant occurredAt) {
		return ofUser(AuditAction.ACCESS_REVOKED, actor, target, occurredAt);
	}

	AuditEvent accessReactivated(User actor, User target, Instant occurredAt) {
		return ofUser(AuditAction.ACCESS_REACTIVATED, actor, target, occurredAt);
	}

	AuditEvent roleCreated(User actor, String roleName, Instant occurredAt) {
		return ofCatalog(AuditAction.ROLE_CREATED, actor, roleName, occurredAt);
	}

	AuditEvent roleDeleted(User actor, String roleName, Instant occurredAt) {
		return ofCatalog(AuditAction.ROLE_DELETED, actor, roleName, occurredAt);
	}

	AuditEvent permissionCreated(User actor, String permissionKey, Instant occurredAt) {
		return ofCatalog(AuditAction.PERMISSION_CREATED, actor, permissionKey, occurredAt);
	}

	AuditEvent permissionAssignedToRole(User actor, String roleName, String permissionKey, Instant occurredAt) {
		String targetLabel = roleName + " <- " + permissionKey;
		return ofCatalog(AuditAction.PERMISSION_ASSIGNED_TO_ROLE, actor, targetLabel, occurredAt);
	}

	AuditEvent permissionRevokedFromRole(User actor, String roleName, String permissionKey, Instant occurredAt) {
		return ofCatalog(AuditAction.PERMISSION_REVOKED_FROM_ROLE, actor, roleName + " <- " + permissionKey,
				occurredAt);
	}

	private static AuditEvent ofUser(AuditAction action, User actor, User target, Instant occurredAt) {
		UUID actorId = actor.userId();
		UUID targetId = target.userId();
		return new AuditEvent(action, actorId, actor.email(), targetId, target.email(), null, occurredAt);
	}

	private static AuditEvent ofCatalog(AuditAction action, User actor, String targetLabel, Instant occurredAt) {
		return new AuditEvent(action, actor.userId(), actor.email(), null, null, targetLabel, occurredAt);
	}

}
