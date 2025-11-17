-- ============================================
-- MIGRAÇÃO: Adicionar suporte a chatLid
-- Solução para WhatsApp Business LID
-- ============================================

-- 1. Adicionar coluna chat_lid
ALTER TABLE chats
    ADD COLUMN IF NOT EXISTS chat_lid VARCHAR(255);

-- 2. Criar índice para performance na busca por chatLid
CREATE INDEX IF NOT EXISTS idx_chat_lid ON chats(chat_lid);

-- 3. Permitir que phone seja NULL (para chats com número oculto)
-- Nota: Se o campo já permitir NULL, esta linha pode ser ignorada
ALTER TABLE chats
    ALTER COLUMN phone DROP NOT NULL;