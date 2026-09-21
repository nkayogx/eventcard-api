package com.kayogx.eventcard;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Chooses the database for the tests:
 *  - if Docker is running  -> a real PostgreSQL database in a Docker container (closest to production);
 *  - if Docker is missing  -> an in-memory H2 database (no installation needed).
 *
 * The PostgreSQL container is started only once and shared by all tests, to keep them fast.
 */
final class TestDatabase {

    private static PostgreSQLContainer postgres;

    private TestDatabase() {
    }

    static void addSettings(DynamicPropertyRegistry settings) {
        if (dockerIsAvailable()) {
            usePostgresInDocker(settings);
        } else {
            useInMemoryH2(settings);
        }
        // Start every test run with fresh, empty tables
        settings.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    private static void usePostgresInDocker(DynamicPropertyRegistry settings) {
        if (postgres == null) {
            System.out.println("[TestDatabase] Docker found - using PostgreSQL in Docker");
            postgres = new PostgreSQLContainer("postgres:16-alpine");
            postgres.start();
        }
        settings.add("spring.datasource.url", postgres::getJdbcUrl);
        settings.add("spring.datasource.username", postgres::getUsername);
        settings.add("spring.datasource.password", postgres::getPassword);
    }

    private static void useInMemoryH2(DynamicPropertyRegistry settings) {
        System.out.println("[TestDatabase] Docker not available - using in-memory H2 database");
        settings.add("spring.datasource.url",
                () -> "jdbc:h2:mem:eventcard;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        settings.add("spring.datasource.username", () -> "sa");
        settings.add("spring.datasource.password", () -> "");
        settings.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
    }

    private static boolean dockerIsAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable dockerProblem) {
            return false;
        }
    }
}
