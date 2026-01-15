BEGIN;

ALTER TABLE chats
DROP CONSTRAINT IF EXISTS unique_phone;

ALTER TABLE chats
    ADD CONSTRAINT unique_chats_web_instance_phone
        UNIQUE (web_instance_id, phone);

CREATE INDEX IF NOT EXISTS idx_chats_web_instance_active_last_msg
    ON chats (web_instance_id, active_in_zapi, last_message_time DESC);

COMMIT;
