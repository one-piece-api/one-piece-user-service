package dev.onepieceapi.userservice.application.port.out;

import dev.onepieceapi.userservice.application.exception.EmailAlreadyRegisteredException;
import dev.onepieceapi.userservice.application.exception.InvitationNotResendableException;
import dev.onepieceapi.userservice.application.exception.UserNotFoundException;
import dev.onepieceapi.userservice.domain.RealmRole;
import dev.onepieceapi.userservice.domain.User;
import dev.onepieceapi.userservice.domain.UserFilter;

import java.util.List;
import java.util.Map;
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

	/**
	 * {@code filter.isEmpty()} keeps the original, fully Keycloak-paginated path
	 * ({@code GET /users?first=&max=}, one call, cost independent of realm size). A
	 * non-empty filter has no single native endpoint covering every combination (role
	 * membership, status and free-text search each need a different Keycloak call, and
	 * status has no server-side filter at all), so it resolves a capped candidate batch
	 * and paginates in memory instead - see
	 * {@code KeycloakUserDirectoryAdapter#loadFilterCandidates} and
	 * {@code docs/adr/0008-users-list-filters.md} for the trade-off this accepts.
	 */
	List<User> findUsers(int offset, int limit, UserFilter filter);

	long countUsers(UserFilter filter);

	/**
	 * @throws UserNotFoundException if no account exists for {@code userId}
	 */
	User findUser(UUID userId);

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

	/**
	 * Re-triggers the identity provider's own invitation email for an account whose
	 * current invitation has gone stale
	 * ({@link dev.onepieceapi.userservice.domain.AccountStatus#INVITATION_EXPIRED},
	 * UF-IDU-03) - the same required actions, a new action-token email, no new identity
	 * or {@code userId}. Gated on the account already being expired, not merely PENDING,
	 * so this never runs while a previously issued link is still valid - see
	 * {@code docs/adr/0004-invitation-expiry-gating.md}.
	 * @throws UserNotFoundException if no account exists for {@code userId}
	 * @throws InvitationNotResendableException if the account is not currently
	 * {@code INVITATION_EXPIRED}
	 */
	User resendInvitation(UUID userId);

	/**
	 * Grants {@code role} to {@code userId} (UF-IDU-15). Idempotent: a role the account
	 * already has is left as-is, not an error.
	 * @throws UserNotFoundException if no account exists for {@code userId}
	 */
	User assignRole(UUID userId, RealmRole role);

	/**
	 * Revokes {@code role} from {@code userId} (UF-IDU-15). Idempotent: a role the
	 * account does not currently have is left as-is, not an error.
	 * @throws UserNotFoundException if no account exists for {@code userId}
	 * @throws dev.onepieceapi.userservice.application.exception.LastRoleException if this
	 * would leave the account with no roles at all
	 * @throws dev.onepieceapi.userservice.application.exception.LastAdministratorException
	 * if {@code role} is ADMIN and this account is the realm's last one
	 */
	User revokeRole(UUID userId, RealmRole role);

	/**
	 * Disables the identity-provider account and invalidates every active session/refresh
	 * token for it (UF-IDU-13) - an access token issued before the call remains valid
	 * until its own (short) expiry, per §12. Idempotent: an already-disabled account is
	 * left as-is.
	 * @throws UserNotFoundException if no account exists for {@code userId}
	 * @throws dev.onepieceapi.userservice.application.exception.LastAdministratorException
	 * if this account holds ADMIN and is the realm's last one (UF-IDU-16)
	 */
	User revokeAccess(UUID userId);

	/**
	 * Re-enables a disabled identity-provider account (UF-IDU-14). Idempotent: an account
	 * that isn't currently disabled is left as-is. Sessions/tokens invalidated by a prior
	 * {@link #revokeAccess} stay invalid - the user must authenticate again.
	 * @throws UserNotFoundException if no account exists for {@code userId}
	 */
	User reactivate(UUID userId);

	/**
	 * The fixed realm roles and the permissions each currently bundles - its Keycloak
	 * composite client-roles (see
	 * {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md}). Powers the UI's
	 * read-only role/permission registry; editing a role's permission set happens in
	 * Keycloak directly for now, not through this port.
	 */
	Map<RealmRole, List<String>> listRolePermissions();

}
