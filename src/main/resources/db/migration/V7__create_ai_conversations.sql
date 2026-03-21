CREATE TABLE ai_conversations
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       VARCHAR(20) NOT NULL,
    content    TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_conversations_user_id ON ai_conversations (user_id);
CREATE INDEX idx_ai_conversations_created_at ON ai_conversations (created_at);