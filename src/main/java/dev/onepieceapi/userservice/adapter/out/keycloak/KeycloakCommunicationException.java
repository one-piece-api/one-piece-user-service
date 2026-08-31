package dev.onepieceapi.userservice.adapter.out.keycloak;

/**
 * Wraps any Keycloak Admin API failure that isn't already translated into a specific
 * application exception (e.g.
 * {@link dev.onepieceapi.userservice.application.exception.EmailAlreadyRegisteredException}
 * for a 409) - connectivity failures, unexpected responses, anything the
 * {@code keycloak-admin-client}/JAX-RS layer throws. Without this, those library-specific
 * exception types would cross {@link KeycloakUserDirectoryAdapter}/
 * {@link KeycloakRoleDirectoryAdapter} and leak into the rest of the codebase, defeating
 * the point of confining Keycloak-specific types to this package.
 *
 * <p>
 * Deliberately a plain unchecked exception, not a {@code one-piece-exception}
 * {@code ApplicationException}: there is no HTTP status more specific than a generic
 * internal error to give a caller yet for "the identity provider is unreachable/
 * misbehaving" (that would be a 502/503, not one of the library's current categories) -
 * see {@code docs/adr/0002-keycloak-failure-translation.md} for the trade-off. It falls
 * through to that library's generic 500 handling, same as any other unanticipated
 * failure.
 */
public class KeycloakCommunicationException extends RuntimeException {

	public KeycloakCommunicationException(String message, Throwable cause) {
		super(message, cause);
	}

}
