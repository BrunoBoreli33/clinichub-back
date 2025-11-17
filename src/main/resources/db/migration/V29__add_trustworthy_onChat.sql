-- ===== SCRIPT SQL PARA ADICIONAR COLUNA is_trustworthy =====
-- Execute este script no banco de dados PostgreSQL/MySQL

-- Para PostgreSQL:
ALTER TABLE chats
    ADD COLUMN IF NOT EXISTS is_trustworthy BOOLEAN NOT NULL DEFAULT false;

-- Para MySQL:
-- ALTER TABLE chats ADD COLUMN is_trustworthy BOOLEAN NOT NULL DEFAULT false;

-- Índice opcional para melhorar performance de queries filtradas por isTrustworthy:
CREATE INDEX IF NOT EXISTS idx_is_trustworthy ON chats(is_trustworthy);

-- Verificar se a coluna foi criada com sucesso:
-- SELECT column_name, data_type, column_default FROM information_schema.columns WHERE table_name = 'chats' AND column_name = 'is_trustworthy';