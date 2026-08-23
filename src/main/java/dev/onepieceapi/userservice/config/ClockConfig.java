package dev.onepieceapi.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * A single injectable {@link Clock}, so anything that needs "now" (e.g. audit event
 * timestamps) can be given a fixed one in tests instead of depending on
 * {@link Clock#systemUTC()} directly.
 */
@Configuration
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
