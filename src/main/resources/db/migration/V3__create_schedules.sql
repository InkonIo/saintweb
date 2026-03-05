-- Active: 1757254728773@@localhost@5432@schedule_db
-- V3: Schedules, Entries, Versions

CREATE TYPE schedule_status AS ENUM ('DRAFT', 'PENDING', 'APPROVED', 'REVISION', 'ARCHIVE');

CREATE TABLE schedule_templates
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE schedules
(
    id          BIGSERIAL PRIMARY KEY,
    branch_id   BIGINT          NOT NULL REFERENCES branches (id) ON DELETE RESTRICT,
    template_id BIGINT REFERENCES schedule_templates (id) ON DELETE SET NULL,
    author_id   BIGINT          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    month       SMALLINT        NOT NULL CHECK (month BETWEEN 1 AND 12),
    year        SMALLINT        NOT NULL CHECK (year >= 2000),
    status      schedule_status NOT NULL DEFAULT 'DRAFT',
    version     SMALLINT        NOT NULL DEFAULT 1,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_schedule_branch_month_year UNIQUE (branch_id, month, year)
);

CREATE TABLE schedule_entries
(
    id          BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT       NOT NULL REFERENCES schedules (id) ON DELETE CASCADE,
    employee_id BIGINT       NOT NULL REFERENCES employees (id) ON DELETE RESTRICT,
    work_date   DATE         NOT NULL,
    shift_type  VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_entry_schedule_employee_date UNIQUE (schedule_id, employee_id, work_date)
);

CREATE TABLE schedule_versions
(
    id             BIGSERIAL PRIMARY KEY,
    schedule_id    BIGINT   NOT NULL REFERENCES schedules (id) ON DELETE CASCADE,
    version_number SMALLINT NOT NULL,
    changed_by     BIGINT   NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    changed_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    status         schedule_status NOT NULL,
    comment        TEXT
);

CREATE INDEX idx_schedules_branch_id ON schedules (branch_id);
CREATE INDEX idx_schedules_status ON schedules (status);
CREATE INDEX idx_schedule_entries_schedule_id ON schedule_entries (schedule_id);
CREATE INDEX idx_schedule_entries_employee_id ON schedule_entries (employee_id);
CREATE INDEX idx_schedule_versions_schedule_id ON schedule_versions (schedule_id);
