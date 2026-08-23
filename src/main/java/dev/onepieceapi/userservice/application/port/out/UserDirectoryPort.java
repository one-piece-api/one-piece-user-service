package dev.onepieceapi.userservice.application.port.out;

import dev.onepieceapi.userservice.domain.UserAccount;

import java.util.List;

/**
 * Outbound port for reading application-user identities (see the identity model in
 * application-user-identity-management.md) from whichever identity provider backs them.
 * The application layer depends only on this interface, never on a specific provider's
 * SDK: Keycloak is one implementation of it, not the only one it is written against.
 * Scoped to exactly UF-IDU-17 (the admin user listing) today; extend it only when a new
 * use case actually needs another identity-provider capability.
 */
public interface UserDirectoryPort {

	List<UserAccount> findUsers(int offset, int limit);

	long countUsers();

}
