-- V5__create_tags_table.sql
CREATE TABLE IF NOT EXISTS tags (
                                    id VARCHAR(36) NOT NULL,
                                    name VARCHAR(50) NOT NULL,
                                    color VARCHAR(7) NOT NULL,
                                    criado_em TIMESTAMP(6) NOT NULL,
                                    atualizado_em TIMESTAMP(6),
                                    user_id VARCHAR(36) NOT NULL,
                                    CONSTRAINT tags_pkey PRIMARY KEY (id),
                                    CONSTRAINT uk_tag_name_user UNIQUE (name, user_id),
                                    CONSTRAINT fk_tags_user FOREIGN KEY (user_id) REFERENCES users(id)
);