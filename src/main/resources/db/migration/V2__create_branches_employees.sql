-- V2: Branches and Employees

CREATE TABLE branches
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    address    VARCHAR(500),
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE employees
(
    id         BIGSERIAL PRIMARY KEY,
    branch_id  BIGINT       NOT NULL REFERENCES branches (id) ON DELETE RESTRICT,
    user_id    BIGINT REFERENCES users (id) ON DELETE SET NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    position   VARCHAR(255),
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_employees_branch_id ON employees (branch_id);
CREATE INDEX idx_employees_user_id ON employees (user_id);
