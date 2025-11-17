ALTER TABLE replies
    ADD COLUMN IF NOT EXISTS original_message_not_found BOOLEAN NOT NULL DEFAULT FALSE;

-- Criar índice para otimizar queries que filtram por este campo (opcional mas recomendado)
CREATE INDEX IF NOT EXISTS idx_replies_original_not_found
    ON replies(original_message_not_found);