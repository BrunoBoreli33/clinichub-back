package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {
    List<Document> findByChatOrderByTimestampAsc(Chat chat);
    Optional<Document> findByMessageId(String messageId);
    Optional<Document> findTopByChatIdOrderByTimestampDesc(String chatId);
    List<Document> findByChatIdOrderByTimestampDesc(String chatId);
}