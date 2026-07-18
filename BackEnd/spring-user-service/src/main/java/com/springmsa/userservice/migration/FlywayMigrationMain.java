package com.springmsa.userservice.migration;

import org.flywaydb.core.Flyway;

import java.util.Map;

public final class FlywayMigrationMain {

    static final String SCHEMA = "user_service";

    private FlywayMigrationMain() {
    }

    public static void main(String[] args) {
        migrate(System.getenv());
        System.out.printf("Flyway migration completed: schema=%s%n", SCHEMA);
    }

    static void migrate(Map<String, String> environment) {
        Flyway.configure()
                .dataSource(
                        required(environment, "SPRING_DATASOURCE_URL"),
                        required(environment, "SPRING_DATASOURCE_USERNAME"),
                        required(environment, "SPRING_DATASOURCE_PASSWORD"))
                .defaultSchema(SCHEMA)
                .schemas(SCHEMA)
                .createSchemas(false)
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required for the Flyway migration task");
        }
        return value;
    }
}
