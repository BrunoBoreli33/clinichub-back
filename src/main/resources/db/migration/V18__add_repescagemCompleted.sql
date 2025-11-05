ALTER TABLE chat_routine_states
    ADD COLUMN IF NOT EXISTS repescagem_completed BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_repescagem_completed ON chat_routine_states(repescagem_completed);