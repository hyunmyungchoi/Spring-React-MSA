package com.springmsa.userservice.migration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserServiceMigrationTest {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("spring_msa")
                    .withUsername("migration_user")
                    .withPassword("migration_password");

    @BeforeAll
    static void startPostgres() throws Exception {
        POSTGRES.start();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA user_service");
        }
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Test
    void createsOnlyUserServiceSchemaObjectsWithoutSeedAccounts() throws Exception {
        FlywayMigrationMain.migrate(Map.of(
                "SPRING_DATASOURCE_URL", POSTGRES.getJdbcUrl(),
                "SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername(),
                "SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword()));

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            assertThat(regclass(statement, "user_service.users")).isEqualTo("user_service.users");
            assertThat(regclass(statement, "user_service.user_roles")).isEqualTo("user_service.user_roles");
            assertThat(regclass(statement, "user_service.flyway_schema_history"))
                    .isEqualTo("user_service.flyway_schema_history");

            try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM user_service.users")) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).isZero();
            }
        }
    }

    private static String regclass(Statement statement, String tableName) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("SELECT to_regclass('" + tableName + "')::text")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
