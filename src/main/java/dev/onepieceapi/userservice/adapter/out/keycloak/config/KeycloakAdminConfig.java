package dev.onepieceapi.userservice.adapter.out.keycloak.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The {@link Keycloak} admin client authenticates lazily (client-credentials grant, on
 * the first actual Admin API call) and refreshes its own token internally - safe to
 * register as a long-lived singleton, with no eager call at startup that would couple
 * this pod's readiness to Keycloak's.
 */
@Configuration
@EnableConfigurationProperties(KeycloakAdminProperties.class)
public class KeycloakAdminConfig {

	@Bean
	Keycloak keycloakAdminClient(KeycloakAdminProperties properties) {
		return KeycloakBuilder.builder()
			.serverUrl(properties.serverUrl())
			.realm(properties.realm())
			.grantType(OAuth2Constants.CLIENT_CREDENTIALS)
			.clientId(properties.clientId())
			.clientSecret(properties.clientSecret())
			.build();
	}

	/**
	 * Backs the independent Admin API calls in {@code KeycloakUserDirectoryAdapter}:
	 * cheap, unbounded-by-design threads for blocking I/O (the admin client above is a
	 * synchronous JAX-RS client) - see implementation-plan.md's Step 3 write-up for why
	 * this was chosen over structured concurrency, still preview on this JDK.
	 */
	@Bean
	ExecutorService keycloakAdminExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

}
