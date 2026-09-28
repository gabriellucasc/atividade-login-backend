CREATE TABLE app_users (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BIT NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    CONSTRAINT uk_app_users_email UNIQUE (email)
);
