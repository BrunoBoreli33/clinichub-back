-- V8__create_routine_texts_table.sql
CREATE TABLE IF NOT EXISTS routine_texts (
                                             id VARCHAR(36) NOT NULL,
                                             sequence_number INTEGER NOT NULL,
                                             text_content TEXT NOT NULL,
                                             hours_delay INTEGER NOT NULL,
                                             created_at TIMESTAMP(6) NOT NULL,
                                             updated_at TIMESTAMP(6) NOT NULL,
                                             user_id VARCHAR(36) NOT NULL,
                                             CONSTRAINT routine_texts_pkey PRIMARY KEY (id),
                                             CONSTRAINT fk_routine_texts_user FOREIGN KEY (user_id) REFERENCES users(id)
);