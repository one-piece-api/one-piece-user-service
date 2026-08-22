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
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	// Page/Pageable for the admin listing (UF-IDU-17) - DB-agnostic, pulled in
	// directly rather than via a JPA starter since this service has no
	// persistence of its own (Keycloak is the sole identity store, see §2 of
	// application-user-identity-management.md). Both are needed as of Boot
	// 4.1's split autoconfigure modules: spring-data-commons for the
	// Page/Pageable types themselves, spring-boot-data-commons for the
	// autoconfiguration that resolves a Pageable controller argument from
	// request params (page/size/sort).
	implementation("org.springframework.data:spring-data-commons")
	implementation("org.springframework.boot:spring-boot-data-commons")
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
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

checkstyle {
	toolVersion = "14.0.0"
}
