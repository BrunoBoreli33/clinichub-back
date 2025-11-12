--ALTER TABLE replies
--    RENAME COLUMN content TO message_content;

ALTER TABLE replies
    ADD COLUMN IF NOT EXISTS mensagem_enviada TEXT;
