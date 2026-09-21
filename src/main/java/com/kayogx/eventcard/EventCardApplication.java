package com.kayogx.eventcard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// We check passwords ourselves (see auth/AuthService), so we switch off
// Spring's built-in "default user with a generated password".
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class EventCardApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventCardApplication.class, args);
	}
}
