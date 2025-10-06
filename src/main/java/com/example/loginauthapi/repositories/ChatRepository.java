package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, String> {

    // Buscar todos os chats de uma WebInstance específica
    List<Chat> findByWebInstanceId(String webInstanceId);

    // Buscar todos os chats de uma WebInstance ordenados por última mensagem
    List<Chat> findByWebInstanceIdOrderByLastMessageTimeDesc(String webInstanceId);

    // Buscar chat específico por phone e instância
    Optional<Chat> findByWebInstanceIdAndPhone(String webInstanceId, String phone);

    // Buscar chats por coluna
    List<Chat> findByWebInstanceIdAndColumn(String webInstanceId, String column);

    // Buscar chats não lidos de uma instância
    @Query("SELECT c FROM Chat c WHERE c.webInstance.id = :webInstanceId AND c.unread > 0")
    List<Chat> findUnreadChatsByWebInstanceId(@Param("webInstanceId") String webInstanceId);

    // Contar chats não lidos
    @Query("SELECT COUNT(c) FROM Chat c WHERE c.webInstance.id = :webInstanceId AND c.unread > 0")
    Long countUnreadByWebInstanceId(@Param("webInstanceId") String webInstanceId);

    // Deletar todos os chats de uma instância (útil para limpeza)
    void deleteByWebInstanceId(String webInstanceId);
}