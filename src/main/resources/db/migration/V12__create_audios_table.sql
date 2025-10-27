-- V12__create_audios_table.sql
-- ou use o número de versão apropriado para seu projeto, ex: V2__, V3__, etc.

CREATE TABLE audios (
                        id VARCHAR(36) PRIMARY KEY,
                        chat_id VARCHAR(36) NOT NULL,
                        message_id VARCHAR(255) NOT NULL UNIQUE,
                        instance_id VARCHAR(255) NOT NULL,
                        connected_phone VARCHAR(255) NOT NULL,
                        phone VARCHAR(255) NOT NULL,
                        from_me BOOLEAN NOT NULL,
                        timestamp TIMESTAMP NOT NULL,
                        seconds INTEGER NOT NULL,
                        audio_url TEXT NOT NULL,
                        mime_type VARCHAR(255),
                        view_once BOOLEAN DEFAULT FALSE,
                        is_status_reply BOOLEAN DEFAULT FALSE,
                        sender_name VARCHAR(255),
                        sender_photo TEXT,
                        status VARCHAR(20),
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP,

                        CONSTRAINT fk_audio_chat FOREIGN KEY (chat_id) REFERENCES chats(id)
);

-- Índices
CREATE INDEX idx_audio_chat_id ON audios(chat_id);
CREATE INDEX idx_audio_message_id ON audios(message_id);
CREATE INDEX idx_audio_timestamp ON audios(timestamp);