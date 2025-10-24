-- V6__create_chat_tags_table.sql
CREATE TABLE IF NOT EXISTS chat_tags (
                                         chat_id VARCHAR(36) NOT NULL,
                                         tag_id VARCHAR(36) NOT NULL,
                                         CONSTRAINT chat_tags_pkey PRIMARY KEY (chat_id, tag_id),
                                         CONSTRAINT fk_chat_tags_chat FOREIGN KEY (chat_id) REFERENCES chats(id),
                                         CONSTRAINT fk_chat_tags_tag FOREIGN KEY (tag_id) REFERENCES tags(id)
);