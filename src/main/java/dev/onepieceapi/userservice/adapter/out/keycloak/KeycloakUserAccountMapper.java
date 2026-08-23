package dev.onepieceapi.userservice.adapter.out.keycloak;

import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.UserAccount;
import lombok.experimental.UtilityClass;
import org.keycloak.representations.idm.UserRepresentation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Maps Keycloak's own {@link UserRepresentation} to the domain {@link UserAccount},
 * including deriving {@link AccountStatus} from Keycloak-specific account state
 * ({@code enabled}, {@code requiredActions}) - knowledge that belongs to this adapter,
 * not to the domain.
 */
@UtilityClass
class KeycloakUserAccountMapper {

	private static final String UPDATE_PASSWORD_REQUIRED_ACTION = "UPDATE_PASSWORD";

	UserAccount toUserAccount(UserRepresentation user, List<String> realmRoles) {
		return new UserAccount(UUID.fromString(user.getId()), user.getEmail(), statusOf(user), realmRoles,
				createdAtOf(user));
	}

	private AccountStatus statusOf(UserRepresentation user) {
		if (Boolean.FALSE.equals(user.isEnabled())) {
			return AccountStatus.DISABLED;
		}
		boolean hasUsableCredential = user.getRequiredActions() == null
				|| !user.getRequiredActions().contains(UPDATE_PASSWORD_REQUIRED_ACTION);
		return hasUsableCredential ? AccountStatus.ACTIVE : AccountStatus.PENDING;
	}

	private Instant createdAtOf(UserRepresentation user) {
		Long createdTimestamp = user.getCreatedTimestamp();
		return createdTimestamp != null ? Instant.ofEpochMilli(createdTimestamp) : null;
	}

}
