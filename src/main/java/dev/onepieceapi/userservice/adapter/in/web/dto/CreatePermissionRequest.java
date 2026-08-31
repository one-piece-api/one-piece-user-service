package dev.onepieceapi.userservice.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * {@code key} must be {@code resource:action}, lowercase - the same shape every existing
 * permission already follows (see {@code Permission}). {@code description} is the
 * human-readable label shown in the registry/matrix; it never affects authorization.
 */
public record CreatePermissionRequest(
		@NotBlank @Pattern(regexp = "^[a-z0-9]+:[a-z0-9]+$", message = "must be resource:action") String key,
		@NotBlank String description) {
}
