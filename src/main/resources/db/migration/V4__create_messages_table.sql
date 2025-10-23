-- V4__create_messages_table.sql
CREATE TABLE IF NOT EXISTS messages (
                                        id VARCHAR(36) NOT NULL,
                                        message_id VARCHAR(255) NOT NULL,
                                        content TEXT NOT NULL,
                                        type VARCHAR(20) NOT NULL,
                                        audio_url TEXT,
                                        audio_duration INTEGER,
                                        from_me BOOLEAN NOT NULL,
                                        timestamp TIMESTAMP(6) NOT NULL,
                                        status VARCHAR(20),
                                        sender_name VARCHAR(255),
                                        sender_photo TEXT,
                                        is_edited BOOLEAN,
                                        is_forwarded BOOLEAN,
                                        is_group BOOLEAN,
                                        created_at TIMESTAMP(6) NOT NULL,
                                        updated_at TIMESTAMP(6),
                                        chat_id VARCHAR(36) NOT NULL,
                                        CONSTRAINT messages_pkey PRIMARY KEY (id),
                                        CONSTRAINT uk_message_id UNIQUE (message_id),
                                        CONSTRAINT fk_messages_chat FOREIGN KEY (chat_id) REFERENCES chats(id)
);

CREATE INDEX IF NOT EXISTS idx_chat_id ON messages(chat_id);
CREATE INDEX IF NOT EXISTS idx_message_id ON messages(message_id);
CREATE INDEX IF NOT EXISTS idx_timestamp ON messages(timestamp);