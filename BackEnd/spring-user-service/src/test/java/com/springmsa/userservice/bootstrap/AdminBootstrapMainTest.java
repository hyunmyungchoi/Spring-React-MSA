package com.springmsa.userservice.bootstrap;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminBootstrapMainTest {

    private static final String PASSWORD = "correct-horse-battery-staple-2026";
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("spring_msa")
                    .withUsername("bootstrap_user")
                    .withPassword("bootstrap_password");

    @BeforeAll
    static void startPostgres() throws Exception {
        POSTGRES.start();
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA user_service");
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("user_service")
                .schemas("user_service")
                .createSchemas(false)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @BeforeEach
    void clearUsers() throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE user_service.user_roles, user_service.users RESTART IDENTITY CASCADE");
        }
    }

    @Test
    void createsExactlyOneEnabledAdminWithBcryptPassword() throws Exception {
        AdminBootstrapMain.BootstrapResult result = AdminBootstrapMain.bootstrap(environment("admin", "admin@example.com"));

        assertThat(result.status()).isEqualTo(AdminBootstrapMain.BootstrapStatus.CREATED);
        assertThat(result.identityFingerprint()).hasSize(24);

        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            try (ResultSet users = statement.executeQuery(
                    "SELECT login_id, email, password, username, enabled FROM user_service.users"
            )) {
                assertThat(users.next()).isTrue();
                assertThat(users.getString("login_id")).isEqualTo("admin");
                assertThat(users.getString("email")).isEqualTo("admin@example.com");
                assertThat(users.getString("username")).isEqualTo("Initial Administrator");
                assertThat(users.getBoolean("enabled")).isTrue();
                assertThat(new BCryptPasswordEncoder().matches(PASSWORD, users.getString("password"))).isTrue();
                assertThat(users.next()).isFalse();
            }

            try (ResultSet roles = statement.executeQuery(
                    "SELECT role FROM user_service.user_roles ORDER BY role"
            )) {
                List<String> roleNames = new ArrayList<>();
                while (roles.next()) {
                    roleNames.add(roles.getString("role"));
                }
                assertThat(roleNames).containsExactly("ROLE_ADMIN", "ROLE_USER");
            }
        }
    }

    @Test
    void sameCredentialsAreIdempotent() throws Exception {
        Map<String, String> environment = environment("admin", "admin@example.com");

        assertThat(AdminBootstrapMain.bootstrap(environment).status())
                .isEqualTo(AdminBootstrapMain.BootstrapStatus.CREATED);
        assertThat(AdminBootstrapMain.bootstrap(environment).status())
                .isEqualTo(AdminBootstrapMain.BootstrapStatus.ALREADY_PRESENT);

        assertThat(count("SELECT COUNT(*) FROM user_service.users")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM user_service.user_roles")).isEqualTo(2);
    }

    @Test
    void refusesASecondDifferentAdmin() throws Exception {
        AdminBootstrapMain.bootstrap(environment("admin", "admin@example.com"));

        assertThatThrownBy(() -> AdminBootstrapMain.bootstrap(environment("other-admin", "other@example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different or invalid ROLE_ADMIN");

        assertThat(count("SELECT COUNT(*) FROM user_service.users")).isEqualTo(1);
    }

    @Test
    void refusesIdentityCollisionWithExistingNonAdmin() throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO user_service.users (
                        login_id, email, password, username, enabled, created_at, updated_at
                    ) VALUES (
                        'admin', 'member@example.com', 'encoded', 'Member', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """);
        }

        assertThatThrownBy(() -> AdminBootstrapMain.bootstrap(environment("admin", "admin@example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requested identity already exists");
    }

    @Test
    void rejectsWeakPasswordBeforeConnecting() {
        Map<String, String> environment = environment("admin", "admin@example.com");
        environment.put("ADMIN_BOOTSTRAP_PASSWORD", "too-short");

        assertThatThrownBy(() -> AdminBootstrapMain.bootstrap(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20 to 72 UTF-8 bytes");
    }

    private static Map<String, String> environment(String loginId, String email) {
        Map<String, String> environment = new HashMap<>();
        environment.put("SPRING_DATASOURCE_URL", POSTGRES.getJdbcUrl());
        environment.put("SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername());
        environment.put("SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword());
        environment.put("ADMIN_BOOTSTRAP_LOGIN_ID", loginId);
        environment.put("ADMIN_BOOTSTRAP_EMAIL", email);
        environment.put("ADMIN_BOOTSTRAP_PASSWORD", PASSWORD);
        environment.put("ADMIN_BOOTSTRAP_USERNAME", "Initial Administrator");
        environment.put("ADMIN_BOOTSTRAP_AUDIT_ACTOR", "arn:aws:iam::123456789012:user/operator");
        environment.put("ADMIN_BOOTSTRAP_REQUEST_ID", "approved-admin-bootstrap-test");
        return environment;
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static int count(String sql) throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
