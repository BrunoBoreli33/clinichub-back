package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    // Buscar mensagens de um chat ordenadas por timestamp
    List<Message> findByChatIdOrderByTimestampAsc(String chatId);

    // ✅ NOVO: Buscar a última mensagem de um chat
    Optional<Message> findTopByChatIdOrderByTimestampDesc(String chatId);

    // Buscar mensagem pelo messageId do WhatsApp
    Optional<Message> findByMessageId(String messageId);

    // Deletar mensagens antigas (mais de 60 dias)
    @Modifying
    @Query("DELETE FROM Message m WHERE m.timestamp < :cutoffDate")
    void deleteOldMessages(@Param("cutoffDate") LocalDateTime cutoffDate);

    // Contar mensagens de um chat
    long countByChatId(String chatId);
}