package dev.onepieceapi.userservice.service;

import dev.onepieceapi.userservice.domain.ApplicationUser;
import dev.onepieceapi.userservice.exception.ApplicationUserNotFoundException;
import dev.onepieceapi.userservice.mapper.ApplicationUserMapper;
import dev.onepieceapi.userservice.persistence.ApplicationUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class ApplicationUserService {

	private final ApplicationUserRepository applicationUserRepository;

	public ApplicationUser findByUserId(UUID userId) {
		return this.applicationUserRepository.findById(userId)
			.map(ApplicationUserMapper::from)
			.orElseThrow(() -> new ApplicationUserNotFoundException(userId));
	}

}
