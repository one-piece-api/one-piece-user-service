package dev.onepieceapi.userservice.mapper;

import dev.onepieceapi.userservice.domain.ApplicationUser;
import dev.onepieceapi.userservice.persistence.entity.ApplicationUserEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ApplicationUserMapper {

	public ApplicationUser from(ApplicationUserEntity entity) {
		return new ApplicationUser(entity.getUserId(), entity.getEmail());
	}

}
