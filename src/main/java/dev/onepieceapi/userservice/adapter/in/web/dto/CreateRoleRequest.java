package dev.onepieceapi.userservice.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code copyFromRole} is optional - a name matching an existing role seeds the new one
 * with that role's current permissions; {@code null}/blank creates it with none.
 * Normalization (uppercase, non-alphanumeric collapsed to {@code _}) and the "does this
 * name already exist" check both happen in {@code RoleManagementService}, not here.
 */
public record CreateRoleRequest(@NotBlank String name, String copyFromRole) {
}
