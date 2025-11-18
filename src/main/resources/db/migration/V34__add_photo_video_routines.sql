-- ============================================================================
-- SCRIPT DE MIGRAÇÃO: Adicionar suporte a fotos e vídeos nas Rotinas Automáticas
-- ============================================================================

-- Adicionar coluna para IDs de fotos
ALTER TABLE routine_texts
    ADD COLUMN IF NOT EXISTS photo_ids TEXT;

-- Adicionar coluna para IDs de vídeos
ALTER TABLE routine_texts
    ADD COLUMN IF NOT EXISTS video_ids TEXT;