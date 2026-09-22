package com.kayogx.eventcard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// We check passwords ourselves (see auth/AuthService), so we switch off
// Spring's built-in "default user with a generated password".
// @EnableScheduling lets background jobs run (e.g. messaging/MessageWorker sending queued messages).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class EventCardApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventCardApplication.class, args);
	}
}
