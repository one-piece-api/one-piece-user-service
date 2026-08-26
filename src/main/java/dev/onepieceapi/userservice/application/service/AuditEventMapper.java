package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.domain.AuditAction;
import dev.onepieceapi.userservice.domain.AuditEvent;
import dev.onepieceapi.userservice.domain.User;
import lombok.experimental.UtilityClass;

import java.time.Instant;

/**
 * Builds audit events (§13 of {@code application-user-identity-management.md}) from the
 * domain objects a use-case service already has on hand, keeping {@link AuditEvent}'s
 * field order/shape out of those services.
 */
@UtilityClass
class AuditEventMapper {

	AuditEvent userInvited(User actor, User invited, Instant occurredAt) {
		return of(AuditAction.USER_INVITED, actor, invited, occurredAt);
	}

	AuditEvent invitationResent(User actor, User target, Instant occurredAt) {
		return of(AuditAction.INVITATION_RESENT, actor, target, occurredAt);
	}

	AuditEvent roleAssigned(User actor, User target, Instant occurredAt) {
		return of(AuditAction.ROLE_ASSIGNED, actor, target, occurredAt);
	}

	AuditEvent roleRevoked(User actor, User target, Instant occurredAt) {
		return of(AuditAction.ROLE_REVOKED, actor, target, occurredAt);
	}

	AuditEvent accessRevoked(User actor, User target, Instant occurredAt) {
		return of(AuditAction.ACCESS_REVOKED, actor, target, occurredAt);
	}

	AuditEvent accessReactivated(User actor, User target, Instant occurredAt) {
		return of(AuditAction.ACCESS_REACTIVATED, actor, target, occurredAt);
	}

	private static AuditEvent of(AuditAction action, User actor, User target, Instant occurredAt) {
		var actorId = actor.userId();
		return new AuditEvent(action, actorId, actor.email(), target.userId(), target.email(), occurredAt);
	}

}
