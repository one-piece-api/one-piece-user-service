package dev.onepieceapi.userservice.adapter.in.web.dto;

import java.util.List;

/**
 * The caller's identity as the application currently resolves it: email from the
 * application user record, roles from the token's current claims (see Step 2 of the
 * implementation plan).
 */
public record MeResponse(String email, List<String> roles) {

}
