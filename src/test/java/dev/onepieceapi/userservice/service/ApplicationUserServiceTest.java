package dev.onepieceapi.userservice.service;

import dev.onepieceapi.userservice.domain.ApplicationUser;
import dev.onepieceapi.userservice.exception.ApplicationUserNotFoundException;
import dev.onepieceapi.userservice.persistence.ApplicationUserRepository;
import dev.onepieceapi.userservice.persistence.entity.ApplicationUserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationUserServiceTest {

	private static final UUID USER_ID = UUID.fromString("446fbe79-5cc4-458d-925d-9934334b6dcf");

	private static final String EMAIL = "luffy@onepiece.local";

	@Mock
	private ApplicationUserRepository applicationUserRepository;

	private ApplicationUserService applicationUserService;

	@BeforeEach
	void setUp() {
		this.applicationUserService = new ApplicationUserService(this.applicationUserRepository);
	}

	@Test
	void mapsThePersistedEntityToTheDomainRecord() {
		var entity = new ApplicationUserEntity(USER_ID, EMAIL);
		when(this.applicationUserRepository.findById(USER_ID)).thenReturn(Optional.of(entity));

		ApplicationUser applicationUser = this.applicationUserService.findByUserId(USER_ID);

		assertThat(applicationUser).isEqualTo(new ApplicationUser(USER_ID, EMAIL));
	}

	@Test
	void throwsWhenNoUserExistsForTheId() {
		when(this.applicationUserRepository.findById(USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.applicationUserService.findByUserId(USER_ID))
			.isInstanceOf(ApplicationUserNotFoundException.class);
	}

}
