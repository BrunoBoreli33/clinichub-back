-- Migration para criar tabela de vídeos
-- Flyway migration: V100__create_videos_table.sql

CREATE TABLE videos (
                        id VARCHAR(255) NOT NULL PRIMARY KEY,
                        chat_id VARCHAR(255) NOT NULL,
                        message_id VARCHAR(255) NOT NULL UNIQUE,
                        instance_id VARCHAR(255) NOT NULL,
                        phone VARCHAR(255) NOT NULL,
                        from_me BOOLEAN NOT NULL,
                        timestamp TIMESTAMP NOT NULL,
                        video_url TEXT NOT NULL,
                        mime_type VARCHAR(255),
                        width INTEGER NOT NULL,
                        height INTEGER NOT NULL,
                        seconds INTEGER NOT NULL,
                        view_once BOOLEAN DEFAULT FALSE,
                        is_gif BOOLEAN DEFAULT FALSE,
                        is_status_reply BOOLEAN DEFAULT FALSE,
                        is_edit BOOLEAN DEFAULT FALSE,
                        is_group BOOLEAN DEFAULT FALSE,
                        is_newsletter BOOLEAN DEFAULT FALSE,
                        forwarded BOOLEAN DEFAULT FALSE,
                        chat_name VARCHAR(255),
                        sender_name VARCHAR(255),
                        status VARCHAR(20),
                        saved_in_gallery BOOLEAN NOT NULL DEFAULT FALSE,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP,
                        CONSTRAINT fk_videos_chat FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
);

-- Criar índices para otimizar consultas
CREATE INDEX idx_video_chat_id ON videos(chat_id);
CREATE INDEX idx_video_message_id ON videos(message_id);
CREATE INDEX idx_video_timestamp ON videos(timestamp);
CREATE INDEX idx_video_saved_in_gallery ON videos(saved_in_gallery);