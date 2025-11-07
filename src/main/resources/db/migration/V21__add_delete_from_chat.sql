-- Migration para adicionar campo deleted_from_chat nas tabelas photos e videos
-- Execute este script no seu banco de dados PostgreSQL

-- Adicionar coluna deleted_from_chat na tabela photos
ALTER TABLE photos
    ADD COLUMN IF NOT EXISTS deleted_from_chat BOOLEAN NOT NULL DEFAULT false;

-- Adicionar coluna deleted_from_chat na tabela videos
ALTER TABLE videos
    ADD COLUMN IF NOT EXISTS deleted_from_chat BOOLEAN NOT NULL DEFAULT false;

-- Comentários para documentação
COMMENT ON COLUMN photos.deleted_from_chat IS 'Flag para soft delete - foto deletada do chat mas mantida na galeria';
COMMENT ON COLUMN videos.deleted_from_chat IS 'Flag para soft delete - vídeo deletado do chat mas mantido na galeria';

-- Criar índices para melhor performance
CREATE INDEX IF NOT EXISTS idx_photo_deleted_from_chat ON photos(deleted_from_chat);
CREATE INDEX IF NOT EXISTS idx_video_deleted_from_chat ON videos(deleted_from_chat);