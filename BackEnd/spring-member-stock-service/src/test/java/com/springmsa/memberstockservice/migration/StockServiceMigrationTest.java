package com.springmsa.memberstockservice.migration;

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

class StockServiceMigrationTest {

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
            statement.execute("CREATE SCHEMA stock_service");
        }
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Test
    void createsStockServiceSchemaObjectsAndIndependentHistory() throws Exception {
        FlywayMigrationMain.migrate(Map.of(
                "SPRING_DATASOURCE_URL", POSTGRES.getJdbcUrl(),
                "SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername(),
                "SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword()));

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            assertThat(regclass(statement, "stock_service.stock_watch_items"))
                    .isEqualTo("stock_service.stock_watch_items");
            assertThat(regclass(statement, "stock_service.flyway_schema_history"))
                    .isEqualTo("stock_service.flyway_schema_history");
        }
    }

    private static String regclass(Statement statement, String tableName) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("SELECT to_regclass('" + tableName + "')::text")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
