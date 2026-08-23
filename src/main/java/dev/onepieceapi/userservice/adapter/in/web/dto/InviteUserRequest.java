package dev.onepieceapi.userservice.adapter.in.web.dto;

import dev.onepieceapi.userservice.domain.RealmRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/**
 * UF-IDU-01: "at least one role must be assigned" is enforced by {@link NotEmpty}; an
 * unrecognized role name fails JSON deserialization before validation even runs, since
 * {@code roles} is typed as {@link RealmRole} rather than {@code String}.
 */
public record InviteUserRequest(@NotBlank @Email String email, @NotEmpty Set<RealmRole> roles) {
}
