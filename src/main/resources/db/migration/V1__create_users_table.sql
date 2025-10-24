-- V1__create_users_table.sql
CREATE TABLE IF NOT EXISTS users (
                                     id VARCHAR(36) NOT NULL,
                                     name VARCHAR(255),
                                     email VARCHAR(255),
                                     password VARCHAR(255),
                                     confirmed BOOLEAN NOT NULL,
                                     confirmation_token VARCHAR(255),
                                     role VARCHAR(255),
                                     CONSTRAINT users_pkey PRIMARY KEY (id)
);