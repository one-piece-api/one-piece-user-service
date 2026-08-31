package dev.onepieceapi.userservice.domain;

/**
 * Optional narrowing for the admin user listing (Step 15): a case-insensitive substring
 * match against username/email, a realm role, an account status - any combination, or
 * none. See {@code UserDirectoryPort#findUsers}/{@code #countUsers} for how a Keycloak
 * adapter applies it.
 */
public record UserFilter(String query, String role, AccountStatus status) {

	private static final UserFilter NONE = new UserFilter(null, null, null);

	public static UserFilter none() {
		return NONE;
	}

	public boolean isEmpty() {
		return (this.query == null || this.query.isBlank()) && this.role == null && this.status == null;
	}

}
