-- ===============================================
-- MIGRATION: Fix Chats Temporários com @lid
-- ===============================================
-- Este script permite que chats temporários sejam
-- salvos no banco com phone contendo @lid
-- ===============================================

-- 1. Remover constraint de unicidade de phone
-- (Necessário porque múltiplos chats podem ter phone NULL ou temporário)
ALTER TABLE chats
    DROP CONSTRAINT IF EXISTS uk_web_instance_phone;
ALTER TABLE chats
    DROP CONSTRAINT IF EXISTS chats_web_instance_id_phone_key;

-- 2. Permitir que phone seja NULL
-- (Para chats temporários que ainda não foram revelados)
ALTER TABLE chats
    ALTER COLUMN phone DROP NOT NULL;

-- 3. Verificar se o índice de chat_lid existe
-- (O índice já deve existir, mas vamos garantir)
CREATE INDEX IF NOT EXISTS idx_chat_lid ON chats(chat_lid);

-- 4. Criar índice composto para busca eficiente
-- (Otimiza busca por web_instance_id + chatLid)
CREATE INDEX IF NOT EXISTS idx_web_instance_chat_lid ON chats(web_instance_id, chat_lid);