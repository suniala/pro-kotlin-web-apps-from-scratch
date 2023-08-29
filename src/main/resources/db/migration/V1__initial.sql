CREATE TABLE user_t
(
    id            BIGSERIAL PRIMARY KEY,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT now(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash BYTEA        NOT NULL
);
