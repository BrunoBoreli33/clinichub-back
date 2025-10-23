-- V2__create_web_instances_table.sql
CREATE TABLE IF NOT EXISTS web_instances (
                                             id VARCHAR(36) NOT NULL,
                                             status VARCHAR(255) NOT NULL,
                                             criado_em TIMESTAMP(6) NOT NULL,
                                             expira_em TIMESTAMP(6),
                                             client_token VARCHAR(255) NOT NULL,
                                             seu_token VARCHAR(255) NOT NULL,
                                             sua_instancia VARCHAR(255) NOT NULL,
                                             connected_phone VARCHAR(20) NOT NULL,
                                             user_id VARCHAR(36) NOT NULL,
                                             CONSTRAINT web_instances_pkey PRIMARY KEY (id),
                                             CONSTRAINT uk_client_token UNIQUE (client_token),
                                             CONSTRAINT uk_seu_token UNIQUE (seu_token),
                                             CONSTRAINT fk_web_instances_user FOREIGN KEY (user_id) REFERENCES users(id)
);