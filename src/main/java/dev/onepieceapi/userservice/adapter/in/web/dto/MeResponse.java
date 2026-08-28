package dev.onepieceapi.userservice.adapter.in.web.dto;

import java.util.List;

/**
 * The caller's identity as the application currently resolves it: username and email from
 * the token's own claims, roles from the token's current claims (see Step 2 of the
 * implementation plan). {@code username} is the user-chosen handle from activation
 * (UF-IDU-02) - the UI's primary identifier for a user, in place of email.
 * {@code permissions} is the caller's own effective permission set (see
 * {@code docs/adr/0007-permissions-as-keycloak-composite-roles.md}), derived from the
 * token the same way roles are - meaningful only for "my own identity", never returned
 * for another user.
 */
public record MeResponse(String username, String email, List<String> roles, List<String> permissions) {

}
