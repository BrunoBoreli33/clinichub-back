-- ================================================
-- Migration: Implementação de Disparo de Campanha
-- Versão: V8__create_campaign_tables.sql
-- Descrição: Cria tabelas para gerenciamento de campanhas de disparo de mensagens
-- ================================================

-- Criar tabela de campanhas
CREATE TABLE IF NOT EXISTS campaigns (
                                         id VARCHAR(255) PRIMARY KEY,
                                         user_id VARCHAR(255) NOT NULL,
                                         name VARCHAR(255) NOT NULL,
                                         message TEXT NOT NULL,
                                         chats_per_dispatch INTEGER NOT NULL,
                                         interval_minutes INTEGER NOT NULL,
                                         status VARCHAR(50) NOT NULL DEFAULT 'CRIADA',
                                         total_chats INTEGER NOT NULL DEFAULT 0,
                                         dispatched_chats INTEGER NOT NULL DEFAULT 0,
                                         next_dispatch_time TIMESTAMP,
                                         tag_ids TEXT,
                                         all_trustworthy BOOLEAN NOT NULL DEFAULT FALSE,
                                         criado_em TIMESTAMP NOT NULL,
                                         atualizado_em TIMESTAMP,
                                         CONSTRAINT fk_campaign_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Criar tabela para armazenar os chats que já receberam disparo de cada campanha
CREATE TABLE IF NOT EXISTS campaign_dispatched_chats (
                                                         campaign_id VARCHAR(255) NOT NULL,
                                                         chat_id VARCHAR(255) NOT NULL,
                                                         PRIMARY KEY (campaign_id, chat_id),
                                                         CONSTRAINT fk_campaign_dispatched_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE,
                                                         CONSTRAINT fk_campaign_dispatched_chat FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
);

-- Criar índices para melhorar performance
CREATE INDEX IF NOT EXISTS idx_campaigns_user_id ON campaigns(user_id);
CREATE INDEX IF NOT EXISTS idx_campaigns_status ON campaigns(status);
CREATE INDEX IF NOT EXISTS idx_campaigns_next_dispatch_time ON campaigns(next_dispatch_time);
CREATE INDEX IF NOT EXISTS idx_campaigns_atualizado_em ON campaigns(atualizado_em DESC);

-- Criar índice composto para queries de campanhas prontas para disparo
CREATE INDEX IF NOT EXISTS idx_campaigns_ready_for_dispatch ON campaigns(status, next_dispatch_time)
    WHERE status = 'EM_ANDAMENTO' AND next_dispatch_time IS NOT NULL;

-- Comentários nas tabelas e colunas para documentação
COMMENT ON TABLE campaigns IS 'Armazena campanhas de disparo de mensagens em massa';
COMMENT ON TABLE campaign_dispatched_chats IS 'Armazena os IDs dos chats que já receberam mensagem de cada campanha';

COMMENT ON COLUMN campaigns.id IS 'Identificador único da campanha';
COMMENT ON COLUMN campaigns.user_id IS 'ID do usuário proprietário da campanha';
COMMENT ON COLUMN campaigns.name IS 'Nome da campanha';
COMMENT ON COLUMN campaigns.message IS 'Mensagem que será enviada na campanha (até 5000 caracteres)';
COMMENT ON COLUMN campaigns.chats_per_dispatch IS 'Quantidade de chats que receberão mensagem em cada disparo';
COMMENT ON COLUMN campaigns.interval_minutes IS 'Intervalo em minutos entre cada lote de disparos';
COMMENT ON COLUMN campaigns.status IS 'Status da campanha: CRIADA, EM_ANDAMENTO, PAUSADA, CONCLUIDA, CANCELADA';
COMMENT ON COLUMN campaigns.total_chats IS 'Total de chats elegíveis para receber a campanha';
COMMENT ON COLUMN campaigns.dispatched_chats IS 'Quantidade de chats que já receberam a mensagem';
COMMENT ON COLUMN campaigns.next_dispatch_time IS 'Data/hora do próximo disparo agendado';
COMMENT ON COLUMN campaigns.tag_ids IS 'IDs das tags selecionadas, separados por vírgula';
COMMENT ON COLUMN campaigns.all_trustworthy IS 'Se true, envia para todos os chats confiáveis (is_trustworthy=true)';
COMMENT ON COLUMN campaigns.criado_em IS 'Data/hora de criação da campanha';
COMMENT ON COLUMN campaigns.atualizado_em IS 'Data/hora da última atualização';