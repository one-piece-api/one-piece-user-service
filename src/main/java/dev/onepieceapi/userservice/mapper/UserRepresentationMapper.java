package dev.onepieceapi.userservice.mapper;

import dev.onepieceapi.userservice.controller.dto.UserSummaryResponse;
import dev.onepieceapi.userservice.domain.AccountStatus;
import lombok.experimental.UtilityClass;
import org.keycloak.representations.idm.UserRepresentation;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class UserRepresentationMapper {

	public UserSummaryResponse toUserSummaryResponse(UserRepresentation user) {
		Long createdTimestamp = user.getCreatedTimestamp();
		Instant createdAt = createdTimestamp != null ? Instant.ofEpochMilli(createdTimestamp) : null;

		return UserSummaryResponse.builder()
			.userId(UUID.fromString(user.getId()))
			.email(user.getEmail())
			.status(AccountStatus.from(user))
			.roles(user.getRealmRoles())
			.createdAt(createdAt)
			.build();
	}

}
