package dev.onepieceapi.userservice.application.exception;

/**
 * Raised when an invite targets an email Keycloak already has an account for (it enforces
 * uniqueness natively, {@code duplicateEmailsAllowed: false} - see §2 of
 * {@code application-user-identity-management.md}). Deliberately provider-agnostic: the
 * application layer and the web adapter only ever see this, never Keycloak's own 409
 * response, which is translated here by {@code KeycloakUserDirectoryAdapter}.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

	public EmailAlreadyRegisteredException(String email) {
		super("An account for " + email + " already exists");
	}

}
