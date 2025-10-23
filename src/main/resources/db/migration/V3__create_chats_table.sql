-- V3__create_chats_table.sql
CREATE TABLE IF NOT EXISTS chats (
                                     id VARCHAR(36) NOT NULL,
                                     name VARCHAR(255),
                                     phone VARCHAR(255) NOT NULL,
                                     last_message_time TIMESTAMP(6),
                                     last_message_content TEXT,
                                     is_group BOOLEAN NOT NULL,
                                     unread INTEGER NOT NULL,
                                     profile_thumbnail TEXT,
                                     column_name VARCHAR(255),
                                     active_in_zapi BOOLEAN NOT NULL,
                                     criado_em TIMESTAMP(6) NOT NULL,
                                     atualizado_em TIMESTAMP(6),
                                     web_instance_id VARCHAR(36) NOT NULL,
                                     CONSTRAINT chats_pkey PRIMARY KEY (id),
                                     CONSTRAINT uk_web_instance_phone UNIQUE (web_instance_id, phone),
                                     CONSTRAINT fk_chats_web_instance FOREIGN KEY (web_instance_id) REFERENCES web_instances(id)
);

CREATE INDEX IF NOT EXISTS idx_web_instance_id ON chats(web_instance_id);
CREATE INDEX IF NOT EXISTS idx_phone ON chats(phone);
CREATE INDEX IF NOT EXISTS idx_last_message_time ON chats(last_message_time);
CREATE INDEX IF NOT EXISTS idx_active_in_zapi ON chats(active_in_zapi);