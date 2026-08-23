package dev.onepieceapi.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A real Postgres (Testcontainers) since the audit trail (Step 4) gave this service its
 * first datasource - without one, the context fails to start (no DataSource to build the
 * JPA/Flyway beans against).
 */
@SpringBootTest
@Testcontainers
class UserServiceApplicationTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	@Test
	void contextLoads() {
	}

}
