-- Migration: Adicionar campo sender_name na tabela replies
-- Data: 2024
-- Descrição: Adiciona a coluna sender_name para armazenar o nome do remetente da mensagem original
-- Banco de Dados: PostgreSQL

-- Adicionar coluna sender_name
ALTER TABLE replies
    ADD COLUMN IF NOT EXISTS sender_name VARCHAR(255);

-- Criar índice para melhor performance (opcional mas recomendado)
CREATE INDEX IF NOT EXISTS idx_replies_sender_name ON replies(sender_name);