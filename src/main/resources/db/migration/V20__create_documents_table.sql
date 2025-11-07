-- Migration para criar tabela de documentos
-- Flyway migration: V101__create_documents_table.sql

CREATE TABLE IF NOT EXISTS documents (
                                         id VARCHAR(255) NOT NULL PRIMARY KEY,
                                         chat_id VARCHAR(255) NOT NULL,
                                         message_id VARCHAR(255) NOT NULL UNIQUE,
                                         instance_id VARCHAR(255) NOT NULL,
                                         phone VARCHAR(255) NOT NULL,
                                         from_me BOOLEAN NOT NULL,
                                         timestamp TIMESTAMP NOT NULL,
                                         document_url TEXT NOT NULL,
                                         file_name VARCHAR(255),
                                         mime_type VARCHAR(100),
                                         page_count INTEGER,
                                         title VARCHAR(255),
                                         caption TEXT,
                                         is_status_reply BOOLEAN DEFAULT FALSE,
                                         is_edit BOOLEAN DEFAULT FALSE,
                                         is_group BOOLEAN DEFAULT FALSE,
                                         is_newsletter BOOLEAN DEFAULT FALSE,
                                         forwarded BOOLEAN DEFAULT FALSE,
                                         chat_name VARCHAR(255),
                                         sender_name VARCHAR(255),
                                         status VARCHAR(20),
                                         created_at TIMESTAMP NOT NULL,
                                         updated_at TIMESTAMP,
                                         CONSTRAINT fk_documents_chat FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
);

-- Criar índices para otimizar consultas
CREATE INDEX IF NOT EXISTS idx_document_chat_id ON documents(chat_id);
CREATE INDEX IF NOT EXISTS idx_document_message_id ON documents(message_id);
CREATE INDEX IF NOT EXISTS idx_document_timestamp ON documents(timestamp);