plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("io.spring.javaformat") version "0.0.48"
	checkstyle
}

group = "dev.onepieceapi"
version = "0.0.1-SNAPSHOT"
description = "One Piece API - application user identity backend"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
	// one-piece-exception (shared error-handling library, docs/adr/0001-exception-library-design.md
	// in that repo) - GitHub Packages requires authentication even to resolve a public package;
	// GITHUB_ACTOR/GITHUB_TOKEN are already set in CI, and a personal PAT with read:packages
	// covers local dev (see that repo's README).
	maven {
		name = "GitHubPackages"
		url = uri("https://maven.pkg.github.com/one-piece-api/one-piece-exception")
		credentials {
			username = System.getenv("GITHUB_ACTOR")
			password = System.getenv("GITHUB_TOKEN")
		}
	}
}

dependencies {
	implementation("dev.onepieceapi:one-piece-exception:0.1.0")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	// Page/Pageable for the admin listing (UF-IDU-17) - DB-agnostic, pulled in
	// directly rather than via the JPA starter below since Keycloak (not this
	// service's own database) is what's actually paginated (see §2 of
	// application-user-identity-management.md). Both are needed as of Boot
	// 4.1's split autoconfigure modules: spring-data-commons for the
	// Page/Pageable types themselves, spring-boot-data-commons for the
	// autoconfiguration that resolves a Pageable controller argument from
	// request params (page/size/sort).
	implementation("org.springframework.data:spring-data-commons")
	implementation("org.springframework.boot:spring-boot-data-commons")
	// The one thing this service does persist itself: the audit trail (§13,
	// starting at Step 4/UF-IDU-01) - see docs/adr/0001-audit-log-persistence.md.
	// Everything else stays Keycloak-derived, unchanged.
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	// Versioned independently of the Keycloak server since it moved to its own
	// repository (github.com/keycloak/keycloak-client) - NOT tied to the
	// 26.6.4 server deployed by onepiece-infrastructure. 26.0.12 is the
	// latest release published to Maven Central at the time this was added;
	// its Admin REST API surface (user/role listing) is stable across these
	// nearby server versions.
	implementation("org.keycloak:keycloak-admin-client:26.0.12")
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	// DataJpaTest + AutoConfigureTestDatabase for JpaAuditLogAdapterTest.
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	// RestTestClient (Boot 4/Spring Framework 7's replacement for the
	// soon-to-be-deprecated TestRestTemplate) for AdminUserListingIntegrationTest's
	// real-server HTTP calls.
	testImplementation("org.springframework.boot:spring-boot-resttestclient")
	// Spins up a real Keycloak (Testcontainers) for AdminUserListingIntegrationTest,
	// instead of mocking the Admin REST API - exercises the real security filter chain
	// and KeycloakClient wiring together. Pinned to the 3.7.x line (Testcontainers
	// 1.20.6) to match the Testcontainers version Spring Boot 4.1 itself manages
	// (1.20.4) - the newer 4.x line requires Testcontainers 2.x, which Boot 4.1 does
	// not yet manage, and mixing major Testcontainers versions risks class/method
	// mismatches at runtime.
	testImplementation("com.github.dasniko:testcontainers-keycloak:3.7.0")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	// Real PostgreSQL (Testcontainers, @ServiceConnection) for JpaAuditLogAdapterTest -
	// exercises the Flyway migration and JPA mapping against the real database engine,
	// not an in-memory substitute. Version managed by Spring Boot's own BOM (unlike the
	// third-party testcontainers-keycloak module above), so no explicit pin needed.
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	// A fake SMTP server for AdminUserListingIntegrationTest's invite scenario, so it can
	// assert Keycloak actually sent the invitation email (UF-IDU-01) instead of stopping
	// at "the Admin API call succeeded" - not managed by Spring Boot's BOM, latest stable
	// pinned explicitly like the other third-party test-only dependencies above.
	testImplementation("com.icegreen:greenmail-junit5:2.1.3")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

checkstyle {
	toolVersion = "14.0.0"
}
