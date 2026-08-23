package dev.onepieceapi.userservice.adapter.in.web.mapper;

import dev.onepieceapi.userservice.adapter.in.web.dto.UserSummaryResponse;
import dev.onepieceapi.userservice.domain.UserAccount;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UserSummaryResponseMapper {

	public UserSummaryResponse toResponse(UserAccount account) {
		return UserSummaryResponse.builder()
			.userId(account.userId())
			.email(account.email())
			.status(account.status())
			.roles(account.roles())
			.createdAt(account.createdAt())
			.build();
	}

}
