package dev.onepieceapi.userservice.adapter.in.web.dto;

/**
 * One entry in the full permission catalog ({@code GET /permissions}) - unlike
 * {@link RolePermissionsResponse}, includes a permission no role currently holds.
 */
public record PermissionResponse(String key, String description) {
}
