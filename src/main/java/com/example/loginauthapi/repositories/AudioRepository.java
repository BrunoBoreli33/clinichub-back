package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.Audio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AudioRepository extends JpaRepository<Audio, String> {

    Optional<Audio> findByMessageId(String messageId);

    // ✅ NOVO: Buscar o último áudio de um chat
    Optional<Audio> findTopByChatIdOrderByTimestampDesc(String chatId);

    List<Audio> findByChatIdOrderByTimestampAsc(String chatId);

    @Query("SELECT a FROM Audio a WHERE a.chat.id = :chatId ORDER BY a.timestamp ASC")
    List<Audio> findAudiosByChatId(String chatId);
}