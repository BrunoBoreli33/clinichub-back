-- ✅ MIGRAÇÃO DO BANCO DE DADOS
-- Script SQL para adicionar os novos campos necessários

-- Adicionar campo de número de upload na tabela users (SE NÃO EXISTIR)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS upload_phone_number VARCHAR(20) NULL;

-- Adicionar campo isUploadChat na tabela chats (SE NÃO EXISTIR)
ALTER TABLE chats
    ADD COLUMN IF NOT EXISTS is_upload_chat BOOLEAN NOT NULL DEFAULT FALSE;

-- Criar índice para melhorar performance de buscas (SE NÃO EXISTIR)
CREATE INDEX IF NOT EXISTS idx_chat_is_upload_chat ON chats(is_upload_chat);

-- Comentários para documentação
COMMENT ON COLUMN users.upload_phone_number IS 'Número de telefone configurado para upload de mídias na galeria';
COMMENT ON COLUMN chats.is_upload_chat IS 'Indica se este chat é usado para upload de mídias (true = chat de upload)';