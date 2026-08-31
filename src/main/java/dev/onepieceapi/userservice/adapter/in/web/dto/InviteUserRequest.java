package dev.onepieceapi.userservice.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/**
 * UF-IDU-01: "at least one role must be assigned" is enforced by {@link NotEmpty}. Roles
 * are dynamic (see {@code docs/adr/0012-role-permission-catalog-management.md}), so an
 * unrecognized role name is no longer caught by deserialization the way a fixed enum
 * would - {@code UserInvitationService} validates each one against the live role catalog
 * instead.
 */
public record InviteUserRequest(@NotBlank @Email String email, @NotEmpty Set<String> roles) {
}
