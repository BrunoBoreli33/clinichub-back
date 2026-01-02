-- 1. Adiciona a coluna com o valor padrão para preencher os registros existentes
ALTER TABLE chats
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'NONE';

-- 2. (Opcional, mas recomendado) Adiciona uma constraint para garantir a integridade no banco
ALTER TABLE chats
    ADD CONSTRAINT chk_chat_routine_status
        CHECK (status IN ('NONE', 'PENDING', 'PROCESSING', 'SENT', 'ERROR'));

-- 3. Cria um índice para a performance da sua query de busca de fila
CREATE INDEX idx_chats_routine_status ON chats(status)
    WHERE status = 'PENDING';