package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, String> {
    Optional<Video> findByMessageId(String messageId);
    List<Video> findByChatIdOrderByTimestampAsc(String chatId);
    List<Video> findByChatWebInstanceUserIdAndSavedInGalleryTrueOrderByTimestampDesc(String userId);
    Optional<Video> findTopByChatIdOrderByTimestampDesc(String chatId);
}