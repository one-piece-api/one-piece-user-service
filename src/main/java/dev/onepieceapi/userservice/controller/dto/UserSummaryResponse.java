package dev.onepieceapi.userservice.controller.dto;

import dev.onepieceapi.userservice.domain.AccountStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record UserSummaryResponse(UUID userId, String email, AccountStatus status, List<String> roles,
		Instant createdAt) {
}
