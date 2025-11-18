-- Script SQL para adicionar suporte a mídias nas campanhas

-- Adicionar colunas photo_ids e video_ids na tabela campaigns
ALTER TABLE campaigns
    ADD COLUMN IF NOT EXISTS photo_ids TEXT,
    ADD COLUMN IF NOT EXISTS video_ids TEXT;