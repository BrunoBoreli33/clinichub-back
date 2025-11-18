ALTER TABLE chats
ADD CONSTRAINT unique_phone UNIQUE (phone);
