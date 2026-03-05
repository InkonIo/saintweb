CREATE TYPE notification_type AS ENUM (
    'SCHEDULE_SUBMITTED',
    'SCHEDULE_APPROVED',
    'SCHEDULE_REVISION'
);

CREATE TABLE notifications
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT            NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    schedule_id BIGINT           NOT NULL REFERENCES schedules (id) ON DELETE CASCADE,
    type       notification_type NOT NULL,
    message    TEXT              NOT NULL,
    is_read    BOOLEAN           NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_id ON notifications (user_id);
CREATE INDEX idx_notifications_is_read ON notifications (is_read);

CREATE TYPE audit_action AS ENUM (
    'SCHEDULE_CREATED',
    'SCHEDULE_UPDATED',
    'SCHEDULE_SUBMITTED',
    'SCHEDULE_APPROVED',
    'SCHEDULE_REVISION',
    'SCHEDULE_ARCHIVED',
    'USER_REGISTERED',
    'USER_LOGIN'
);

CREATE TABLE audit_log
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    action      audit_action NOT NULL,
    entity_type VARCHAR(50),
    entity_id   BIGINT,
    details     TEXT,
    ip_address  VARCHAR(50),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_user_id ON audit_log (user_id);
CREATE INDEX idx_audit_log_action ON audit_log (action);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);