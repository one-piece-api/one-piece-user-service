package dev.onepieceapi.userservice.controller.dto;

import java.util.List;

/**
 * The caller's identity as the application currently records it: status and roles come
 * from the application user record (see Step 2 of the implementation plan), not raw token
 * claims.
 */
public record MeResponse(String email, String status, List<String> roles) {

}
