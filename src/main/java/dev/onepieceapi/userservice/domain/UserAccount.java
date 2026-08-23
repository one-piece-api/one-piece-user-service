package dev.onepieceapi.userservice.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The domain-level read model behind the admin user listing (UF-IDU-17): an identity's
 * userId, email, account status, current roles and creation time, sourced entirely from
 * the identity provider behind {@code UserDirectoryPort} - never persisted locally (see
 * §2 of {@code application-user-identity-management.md}).
 */
public record UserAccount(UUID userId, String email, AccountStatus status, List<String> roles, Instant createdAt) {

}
