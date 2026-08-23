package dev.onepieceapi.userservice.application.port.out;

import dev.onepieceapi.userservice.application.exception.EmailAlreadyRegisteredException;
import dev.onepieceapi.userservice.domain.RealmRole;
import dev.onepieceapi.userservice.domain.User;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound port for reading and provisioning application-user identities (see the
 * identity model in application-user-identity-management.md) from whichever identity
 * provider backs them. The application layer depends only on this interface, never on a
 * specific provider's SDK: Keycloak is one implementation of it, not the only one it is
 * written against. Extend it only when a new use case actually needs another
 * identity-provider capability.
 */
public interface UserDirectoryPort {

	List<User> findUsers(int offset, int limit);

	long countUsers();

	/**
	 * Provisions a new identity with no usable credential and the given roles, then
	 * triggers the identity provider's own invitation email mechanism (UF-IDU-01) - no
	 * token, link or expiry is generated or stored by this application; see
	 * {@code KeycloakUserDirectoryAdapter} for how Keycloak implements this.
	 * @throws EmailAlreadyRegisteredException if the identity provider already has an
	 * account for {@code email}
	 */
	User inviteUser(String email, Set<RealmRole> roles);

	/**
	 * Compensating action for a failed invitation (UF-IDU-01): removes an identity that
	 * {@link #inviteUser} provisioned but the invitation could not be completed for (e.g.
	 * the audit record failed to persist) - restores "no account exists for this email"
	 * so the operation is all-or-nothing from the caller's perspective. Never used for
	 * anything else - revoking an activated user is UF-IDU-13 (disable, not delete).
	 */
	void rollbackInvitation(UUID userId);

}
