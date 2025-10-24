-- V9__create_chat_routine_states_table.sql
CREATE TABLE IF NOT EXISTS chat_routine_states (
                                                   id VARCHAR(36) NOT NULL,
                                                   previous_column VARCHAR(255) NOT NULL,
                                                   last_routine_sent INTEGER NOT NULL,
                                                   last_automated_message_sent TIMESTAMP(6),
                                                   last_user_message_time TIMESTAMP(6),
                                                   in_repescagem BOOLEAN NOT NULL,
                                                   created_at TIMESTAMP(6) NOT NULL,
                                                   updated_at TIMESTAMP(6) NOT NULL,
                                                   chat_id VARCHAR(36) NOT NULL,
                                                   user_id VARCHAR(36) NOT NULL,
                                                   CONSTRAINT chat_routine_states_pkey PRIMARY KEY (id),
                                                   CONSTRAINT fk_chat_routine_states_chat FOREIGN KEY (chat_id) REFERENCES chats(id),
                                                   CONSTRAINT fk_chat_routine_states_user FOREIGN KEY (user_id) REFERENCES users(id)
);