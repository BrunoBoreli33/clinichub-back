ALTER TABLE chat_routine_states
    ADD COLUMN IF NOT EXISTS scheduled_send_time TIMESTAMP(6);