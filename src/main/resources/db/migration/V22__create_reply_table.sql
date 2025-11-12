-- Migration: V102__create_replies_table.sql
-- Criar tabela de replies para armazenar respostas a mensagens

CREATE TABLE IF NOT EXISTS replies (
                                       id VARCHAR(255) NOT NULL PRIMARY KEY,
                                       message_id VARCHAR(255) NOT NULL,
                                       reference_message_id VARCHAR(255) NOT NULL,
                                       chat_id VARCHAR(255) NOT NULL,
                                       content TEXT,
                                       audio_url TEXT,
                                       document_url TEXT,
                                       image_url TEXT,
                                       video_url TEXT,
                                       reply_type VARCHAR(20) NOT NULL,
                                       from_me BOOLEAN NOT NULL DEFAULT FALSE,
                                       timestamp TIMESTAMP NOT NULL,
                                       created_at TIMESTAMP NOT NULL,
                                       updated_at TIMESTAMP,
                                       CONSTRAINT fk_reply_message FOREIGN KEY (message_id)
                                           REFERENCES messages(id) ON DELETE CASCADE,
                                       CONSTRAINT fk_reply_chat FOREIGN KEY (chat_id)
                                           REFERENCES chats(id) ON DELETE CASCADE
);

-- Criar índices para otimizar consultas
CREATE INDEX IF NOT EXISTS idx_reply_message_id ON replies(message_id);
CREATE INDEX IF NOT EXISTS idx_reply_reference_message_id ON replies(reference_message_id);
CREATE INDEX IF NOT EXISTS idx_reply_chat_id ON replies(chat_id);
CREATE INDEX IF NOT EXISTS idx_reply_timestamp ON replies(timestamp);
CREATE INDEX IF NOT EXISTS idx_reply_type ON replies(reply_type);