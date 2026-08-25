package dev.onepieceapi.userservice.adapter.in.web.dto;

import java.util.List;

/**
 * The caller's identity as the application currently resolves it: username and email from
 * the token's own claims, roles from the token's current claims (see Step 2 of the
 * implementation plan). {@code username} is the user-chosen handle from activation
 * (UF-IDU-02) - the UI's primary identifier for a user, in place of email.
 */
public record MeResponse(String username, String email, List<String> roles) {

}
