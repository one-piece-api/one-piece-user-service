package dev.onepieceapi.userservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link KeycloakRoleProperties}, kept separate from admin-client wiring. */
@Configuration
@EnableConfigurationProperties(KeycloakRoleProperties.class)
public class KeycloakRoleConfig {

}
