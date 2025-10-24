-- V10__add_previous_column_to_chats.sql
-- Adicionar coluna previous_column na tabela chats para funcionalidade de Tarefas

ALTER TABLE chats
    ADD COLUMN previous_column VARCHAR(255) NULL;

-- Comentário explicativo (opcional, para PostgreSQL)
COMMENT ON COLUMN chats.previous_column IS 'Coluna anterior antes de ir para tarefa';