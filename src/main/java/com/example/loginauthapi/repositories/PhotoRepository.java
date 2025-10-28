package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, String> {
    Optional<Photo> findByMessageId(String messageId);
    List<Photo> findByChatIdOrderByTimestampAsc(String chatId);
    List<Photo> findByChatWebInstanceUserIdAndSavedInGalleryTrueOrderByTimestampDesc(String userId);

    // ✅ NOVO: Buscar última foto de um chat (para syncLastMessageContent)
    Optional<Photo> findTopByChatIdOrderByTimestampDesc(String chatId);
}