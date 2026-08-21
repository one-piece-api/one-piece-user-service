package dev.onepieceapi.userservice.persistence;

import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.persistence.entity.ApplicationUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real Postgres (Testcontainers), not an in-memory substitute: the
 * migrations (Flyway) and the entity mapping are exercised exactly as they run against
 * the actual PostgreSQL version deployed by {@code onepiece-infrastructure}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ApplicationUserRepositoryTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	@Autowired
	private ApplicationUserRepository applicationUserRepository;

	@Test
	void theBootstrapAdminSeededByFlywayIsPersistedAndActive() {
		UUID bootstrapUserId = UUID.fromString("446fbe79-5cc4-458d-925d-9934334b6dcf");

		Optional<ApplicationUserEntity> luffy = this.applicationUserRepository.findById(bootstrapUserId);

		assertThat(luffy).isPresent();
		assertThat(luffy.get().getEmail()).isEqualTo("luffy@onepiece.local");
		assertThat(luffy.get().getStatus()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	void persistsAUserRecordingCreationAndUpdateTimestamps() {
		var user = new ApplicationUserEntity(UUID.randomUUID(), "nami@onepiece.local", AccountStatus.PENDING);

		ApplicationUserEntity saved = this.applicationUserRepository.saveAndFlush(user);

		assertThat(saved.getStatus()).isEqualTo(AccountStatus.PENDING);
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
	}

	@Test
	void rejectsADuplicateEmail() {
		String email = "zoro@onepiece.local";
		var first = new ApplicationUserEntity(UUID.randomUUID(), email, AccountStatus.ACTIVE);
		var second = new ApplicationUserEntity(UUID.randomUUID(), email, AccountStatus.PENDING);
		this.applicationUserRepository.saveAndFlush(first);

		assertThatThrownBy(() -> this.applicationUserRepository.saveAndFlush(second))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
