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
		return new AuditEvent(AuditAction.USER_INVITED, actor.userId(), actor.email(), invited.userId(),
				invited.email(), occurredAt);
	}

	AuditEvent invitationResent(User actor, User target, Instant occurredAt) {
		return new AuditEvent(AuditAction.INVITATION_RESENT, actor.userId(), actor.email(), target.userId(),
				target.email(), occurredAt);
	}

}
