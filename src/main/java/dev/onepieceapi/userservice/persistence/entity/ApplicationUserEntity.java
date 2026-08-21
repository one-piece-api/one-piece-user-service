package dev.onepieceapi.userservice.persistence.entity;

import dev.onepieceapi.userservice.domain.ApplicationUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence mapping for the {@code application_user} table (see §2 of
 * {@code application-user-identity-management.md}). This is a JPA implementation detail:
 * outside the {@code persistence} packages, application/business code should never depend
 * on it directly — it consumes the {@link ApplicationUser} domain record instead,
 * obtained via {@code ApplicationUserService} ({@code ApplicationUserMapper} does the
 * conversion). Credentials, TOTP, roles, account status and email verification live only
 * in Keycloak and are deliberately not mirrored here — see {@link ApplicationUser} for
 * why.
 */
@Getter
@Entity
@Table(name = "application_user")
public class ApplicationUserEntity {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Column(nullable = false, unique = true)
	private String email;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ApplicationUserEntity() {
	}

	public ApplicationUserEntity(UUID userId, String email) {
		this.userId = userId;
		this.email = email;
	}

}
