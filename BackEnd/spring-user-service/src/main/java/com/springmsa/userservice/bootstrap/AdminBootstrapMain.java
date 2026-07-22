package com.springmsa.userservice.bootstrap;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class AdminBootstrapMain {

    private static final long ADVISORY_LOCK_KEY = 7_240_415_531_100_001L;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern AUDIT_VALUE_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/+=,-]{1,200}$");
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);

    private AdminBootstrapMain() {
    }

    public static void main(String[] args) throws Exception {
        BootstrapResult result = bootstrap(System.getenv());
        System.out.printf(
                "admin-bootstrap result=%s actor=%s request_id=%s identity_fingerprint=%s%n",
                result.status().logValue(),
                result.auditActor(),
                result.requestId(),
                result.identityFingerprint()
        );
    }

    static BootstrapResult bootstrap(Map<String, String> environment) throws Exception {
        BootstrapConfig config = BootstrapConfig.from(environment);

        try (Connection connection = DriverManager.getConnection(
                config.datasourceUrl(),
                config.datasourceUsername(),
                config.datasourcePassword()
        )) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

            try {
                acquireLock(connection);
                BootstrapStatus status = createOrVerifyAdmin(connection, config);
                connection.commit();
                return new BootstrapResult(
                        status,
                        config.auditActor(),
                        config.requestId(),
                        fingerprint(config.loginId(), config.email())
                );
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void acquireLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            statement.setLong(1, ADVISORY_LOCK_KEY);
            statement.execute();
        }
    }

    private static BootstrapStatus createOrVerifyAdmin(
            Connection connection,
            BootstrapConfig config
    ) throws SQLException {
        AdminAccount existingAdmin = findSingleAdmin(connection);
        if (existingAdmin != null) {
            verifyIdempotentRetry(connection, config, existingAdmin);
            return BootstrapStatus.ALREADY_PRESENT;
        }

        refuseIdentityCollision(connection, config);
        long userId = insertAdmin(connection, config);
        insertRole(connection, userId, "ROLE_USER");
        insertRole(connection, userId, "ROLE_ADMIN");
        return BootstrapStatus.CREATED;
    }

    private static AdminAccount findSingleAdmin(Connection connection) throws SQLException {
        String sql = """
                SELECT u.user_id, u.login_id, u.email, u.password, u.enabled
                FROM user_service.users u
                JOIN user_service.user_roles r ON r.user_id = u.user_id
                WHERE r.role = 'ROLE_ADMIN'
                ORDER BY u.user_id
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return null;
            }

            AdminAccount account = new AdminAccount(
                    resultSet.getLong("user_id"),
                    resultSet.getString("login_id"),
                    resultSet.getString("email"),
                    resultSet.getString("password"),
                    resultSet.getBoolean("enabled")
            );

            if (resultSet.next()) {
                throw new IllegalStateException("Admin bootstrap refused because multiple ROLE_ADMIN accounts exist");
            }
            return account;
        }
    }

    private static void verifyIdempotentRetry(
            Connection connection,
            BootstrapConfig config,
            AdminAccount account
    ) throws SQLException {
        boolean sameIdentity = account.loginId().equals(config.loginId())
                && account.email().equals(config.email());
        boolean samePassword = PASSWORD_ENCODER.matches(config.password(), account.encodedPassword());
        Set<String> roles = findRoles(connection, account.userId());

        if (!sameIdentity
                || !samePassword
                || !account.enabled()
                || !roles.containsAll(Set.of("ROLE_USER", "ROLE_ADMIN"))) {
            throw new IllegalStateException("Admin bootstrap refused because a different or invalid ROLE_ADMIN account exists");
        }
    }

    private static Set<String> findRoles(Connection connection, long userId) throws SQLException {
        String sql = "SELECT role FROM user_service.user_roles WHERE user_id = ?";
        Set<String> roles = new java.util.HashSet<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    roles.add(resultSet.getString("role"));
                }
            }
        }
        return roles;
    }

    private static void refuseIdentityCollision(Connection connection, BootstrapConfig config) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM user_service.users
                    WHERE login_id = ? OR email = ?
                )
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, config.loginId());
            statement.setString(2, config.email());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (resultSet.getBoolean(1)) {
                    throw new IllegalStateException("Admin bootstrap refused because the requested identity already exists");
                }
            }
        }
    }

    private static long insertAdmin(Connection connection, BootstrapConfig config) throws SQLException {
        String sql = """
                INSERT INTO user_service.users (
                    login_id,
                    email,
                    password,
                    username,
                    enabled,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING user_id
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, config.loginId());
            statement.setString(2, config.email());
            statement.setString(3, PASSWORD_ENCODER.encode(config.password()));
            statement.setString(4, config.username());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong("user_id");
            }
        }
    }

    private static void insertRole(Connection connection, long userId, String role) throws SQLException {
        String sql = "INSERT INTO user_service.user_roles (user_id, role) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, role);
            statement.executeUpdate();
        }
    }

    private static String fingerprint(String loginId, String email) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest((loginId + "\u0000" + email).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash, 0, 12);
    }

    enum BootstrapStatus {
        CREATED("created"),
        ALREADY_PRESENT("already_present");

        private final String logValue;

        BootstrapStatus(String logValue) {
            this.logValue = logValue;
        }

        String logValue() {
            return logValue;
        }
    }

    record BootstrapResult(
            BootstrapStatus status,
            String auditActor,
            String requestId,
            String identityFingerprint
    ) {
    }

    private record AdminAccount(
            long userId,
            String loginId,
            String email,
            String encodedPassword,
            boolean enabled
    ) {
    }

    record BootstrapConfig(
            String datasourceUrl,
            String datasourceUsername,
            String datasourcePassword,
            String loginId,
            String email,
            String password,
            String username,
            String auditActor,
            String requestId
    ) {
        static BootstrapConfig from(Map<String, String> environment) {
            String password = required(environment, "ADMIN_BOOTSTRAP_PASSWORD", false);
            int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
            if (passwordBytes < 20 || passwordBytes > 72) {
                throw new IllegalArgumentException("ADMIN_BOOTSTRAP_PASSWORD must be 20 to 72 UTF-8 bytes");
            }

            String loginId = normalized(environment, "ADMIN_BOOTSTRAP_LOGIN_ID", 50);
            String email = normalized(environment, "ADMIN_BOOTSTRAP_EMAIL", 255).toLowerCase(Locale.ROOT);
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new IllegalArgumentException("ADMIN_BOOTSTRAP_EMAIL must be a valid email address");
            }

            String auditActor = auditValue(environment, "ADMIN_BOOTSTRAP_AUDIT_ACTOR");
            String requestId = auditValue(environment, "ADMIN_BOOTSTRAP_REQUEST_ID");

            return new BootstrapConfig(
                    required(environment, "SPRING_DATASOURCE_URL", true),
                    required(environment, "SPRING_DATASOURCE_USERNAME", true),
                    required(environment, "SPRING_DATASOURCE_PASSWORD", false),
                    loginId,
                    email,
                    password,
                    normalized(environment, "ADMIN_BOOTSTRAP_USERNAME", 100),
                    auditActor,
                    requestId
            );
        }

        private static String normalized(Map<String, String> environment, String name, int maxLength) {
            String value = required(environment, name, true);
            if (value.length() > maxLength) {
                throw new IllegalArgumentException(name + " must be " + maxLength + " characters or less");
            }
            return value;
        }

        private static String auditValue(Map<String, String> environment, String name) {
            String value = required(environment, name, true);
            if (!AUDIT_VALUE_PATTERN.matcher(value).matches()) {
                throw new IllegalArgumentException(name + " contains unsupported audit characters");
            }
            return value;
        }

        private static String required(Map<String, String> environment, String name, boolean trim) {
            String value = environment.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " is required for the admin bootstrap task");
            }
            return trim ? value.trim() : value;
        }
    }
}
