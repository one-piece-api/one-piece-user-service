package dev.onepieceapi.userservice.identity;

import java.util.List;

/**
 * The caller's identity as carried by the current access token: no application database
 * involved yet (see Step 1 of the implementation plan).
 */
record MeResponse(String email, List<String> roles) {

}
