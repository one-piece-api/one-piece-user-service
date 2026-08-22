package dev.onepieceapi.userservice.domain;

import org.keycloak.representations.idm.UserRepresentation;

/**
 * Conceptual account status (§10 of {@code application-user-identity-management.md}) -
 * never a stored field (see {@link ApplicationUser}), always derived from the Keycloak
 * account's own state.
 */
public enum AccountStatus {

	PENDING, ACTIVE, DISABLED;

	private static final String UPDATE_PASSWORD_REQUIRED_ACTION = "UPDATE_PASSWORD";

	public static AccountStatus from(UserRepresentation user) {
		if (Boolean.FALSE.equals(user.isEnabled())) {
			return AccountStatus.DISABLED;
		}
		boolean hasUsableCredential = user.getRequiredActions() == null
				|| !user.getRequiredActions().contains(UPDATE_PASSWORD_REQUIRED_ACTION);
		return hasUsableCredential ? AccountStatus.ACTIVE : AccountStatus.PENDING;

	}

}
