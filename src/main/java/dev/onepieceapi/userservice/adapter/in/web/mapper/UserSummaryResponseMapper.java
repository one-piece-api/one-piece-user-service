package dev.onepieceapi.userservice.adapter.in.web.mapper;

import dev.onepieceapi.userservice.adapter.in.web.dto.UserSummaryResponse;
import dev.onepieceapi.userservice.domain.User;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UserSummaryResponseMapper {

	public UserSummaryResponse toResponse(User user) {
		return UserSummaryResponse.builder()
			.userId(user.userId())
			.username(user.username())
			.email(user.email())
			.status(user.status())
			.roles(user.roles())
			.createdAt(user.createdAt())
			.build();
	}

}
