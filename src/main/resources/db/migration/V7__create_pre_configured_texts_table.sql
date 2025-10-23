-- V7__create_pre_configured_texts_table.sql
CREATE TABLE IF NOT EXISTS pre_configured_texts (
                                                    id VARCHAR(36) NOT NULL,
                                                    title VARCHAR(255) NOT NULL,
                                                    content TEXT NOT NULL,
                                                    created_at TIMESTAMP(6) NOT NULL,
                                                    updated_at TIMESTAMP(6) NOT NULL,
                                                    user_id VARCHAR(36) NOT NULL,
                                                    CONSTRAINT pre_configured_texts_pkey PRIMARY KEY (id),
                                                    CONSTRAINT fk_pre_configured_texts_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_user_id ON pre_configured_texts(user_id);