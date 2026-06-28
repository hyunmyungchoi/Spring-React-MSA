CREATE TABLE IF NOT EXISTS users (
    user_id BIGSERIAL PRIMARY KEY,
    login_id VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    username VARCHAR(100) NOT NULL,
    phone_number VARCHAR(30),
    whatsapp_number VARCHAR(30),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON DELETE CASCADE
);

INSERT INTO users (
    login_id,
    email,
    password,
    username,
    phone_number,
    whatsapp_number,
    enabled,
    created_at,
    updated_at
) VALUES (
    'user',
    'user@test.com',
    '$2a$10$78NjeFaqL97eNcQ69Rkpt.fSecHhpLf6KnwtoAVC4JDLplE1xUzB2',
    'Test User',
    '01012345678',
    '01012345678',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (login_id) DO NOTHING;

INSERT INTO user_roles (user_id, role)
SELECT user_id, 'ROLE_USER'
FROM users
WHERE login_id = 'user'
ON CONFLICT DO NOTHING;

INSERT INTO users (
    login_id,
    email,
    password,
    username,
    phone_number,
    whatsapp_number,
    enabled,
    created_at,
    updated_at
) VALUES (
    'admin',
    'admin@test.com',
    '$2a$10$78NjeFaqL97eNcQ69Rkpt.fSecHhpLf6KnwtoAVC4JDLplE1xUzB2',
    'Admin User',
    '01012345679',
    '01012345679',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (login_id) DO NOTHING;

INSERT INTO user_roles (user_id, role)
SELECT user_id, role
FROM users
CROSS JOIN (
    VALUES
        ('ROLE_USER'),
        ('ROLE_ADMIN')
) AS roles(role)
WHERE login_id = 'admin'
ON CONFLICT DO NOTHING;
