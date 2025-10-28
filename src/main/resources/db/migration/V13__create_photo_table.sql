-- Migration para criar tabela de fotos (PostgreSQL)
-- Flyway Migration

CREATE TABLE photos (
                        id VARCHAR(36) NOT NULL PRIMARY KEY,
                        chat_id VARCHAR(36) NOT NULL,
                        message_id VARCHAR(255) NOT NULL UNIQUE,
                        instance_id VARCHAR(255) NOT NULL,
                        phone VARCHAR(20) NOT NULL,
                        from_me BOOLEAN NOT NULL,
                        timestamp TIMESTAMP NOT NULL,
                        image_url TEXT NOT NULL,
                        width INT NOT NULL,
                        height INT NOT NULL,
                        mime_type VARCHAR(50),
                        is_status_reply BOOLEAN NOT NULL DEFAULT FALSE,
                        is_edit BOOLEAN NOT NULL DEFAULT FALSE,
                        is_group BOOLEAN NOT NULL DEFAULT FALSE,
                        is_newsletter BOOLEAN NOT NULL DEFAULT FALSE,
                        forwarded BOOLEAN NOT NULL DEFAULT FALSE,
                        chat_name VARCHAR(255),
                        sender_name VARCHAR(255),
                        status VARCHAR(20),
                        saved_in_gallery BOOLEAN NOT NULL DEFAULT FALSE,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP,

                        CONSTRAINT fk_photo_chat FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
);

-- Índices para melhor performance
CREATE INDEX idx_photo_chat_id ON photos(chat_id);
CREATE INDEX idx_photo_message_id ON photos(message_id);
CREATE INDEX idx_photo_timestamp ON photos(timestamp);
CREATE INDEX idx_photo_saved_in_gallery ON photos(saved_in_gallery);