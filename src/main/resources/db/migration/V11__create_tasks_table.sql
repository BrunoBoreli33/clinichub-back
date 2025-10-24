-- V11__create_tasks_table.sql
-- Criar tabela tasks para funcionalidade de agendamento de mensagens
-- ✅ CORRIGIDO: Removida constraint UNIQUE em chat_id para permitir múltiplas tarefas por chat

CREATE TABLE IF NOT EXISTS tasks (
                                     id VARCHAR(36) NOT NULL,
                                     chat_id VARCHAR(36) NOT NULL,
                                     message TEXT NOT NULL,
                                     scheduled_date TIMESTAMP(6) NOT NULL,
                                     executed BOOLEAN NOT NULL DEFAULT FALSE,
                                     executed_at TIMESTAMP(6),
                                     created_at TIMESTAMP(6) NOT NULL,
                                     updated_at TIMESTAMP(6) NOT NULL,
                                     CONSTRAINT tasks_pkey PRIMARY KEY (id),
    -- ✅ REMOVIDO: CONSTRAINT uk_tasks_chat_id UNIQUE (chat_id)
                                     CONSTRAINT fk_tasks_chat FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
);

-- Índices para otimizar queries do scheduler
CREATE INDEX IF NOT EXISTS idx_tasks_scheduled_date ON tasks(scheduled_date);
CREATE INDEX IF NOT EXISTS idx_tasks_executed ON tasks(executed);
CREATE INDEX IF NOT EXISTS idx_tasks_pending ON tasks(executed, scheduled_date);

-- ✅ NOVO: Índice não único para otimizar buscas por chat_id (substituindo o UNIQUE)
CREATE INDEX IF NOT EXISTS idx_tasks_chat_id ON tasks(chat_id);

-- ✅ NOVO: Índice composto para buscar tarefas pendentes de um chat específico
CREATE INDEX IF NOT EXISTS idx_tasks_chat_executed ON tasks(chat_id, executed);

-- ✅ NOVO: Índice para ordenação por data de criação (usado para retornar tarefas mais recentes primeiro)
CREATE INDEX IF NOT EXISTS idx_tasks_created_at ON tasks(created_at DESC);