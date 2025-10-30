-- Migration para adicionar coluna caption na tabela photos
-- Flyway Migration
-- Adiciona suporte para salvar o comentário/legenda das fotos enviadas via WhatsApp

ALTER TABLE photos
    ADD COLUMN IF NOT EXISTS caption TEXT;

-- Comentário explicativo da coluna
COMMENT ON COLUMN photos.caption IS 'Comentário/legenda da foto enviada ou recebida via WhatsApp';