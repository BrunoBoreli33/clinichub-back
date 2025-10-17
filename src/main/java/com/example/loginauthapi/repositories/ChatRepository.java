package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, String> {

    // ============================================
    // MÉTODOS EXISTENTES (mantidos)
    // ============================================

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

    // ============================================
    // ✅ NOVOS MÉTODOS PARA CONTROLE DE active_in_zapi
    // ============================================

    /**
     * Buscar apenas chats ativos (active_in_zapi = true) de uma instância
     * Ordenados por última mensagem (mais recentes primeiro)
     */
    List<Chat> findByWebInstanceIdAndActiveInZapiTrueOrderByLastMessageTimeDesc(String webInstanceId);

    /**
     * Desativar todos os chats de uma instância
     * Define active_in_zapi = false para todos os chats da instância
     * Usado antes de sincronizar com Z-API para "limpar" chats antigos
     */
    @Modifying
    @Query("UPDATE Chat c SET c.activeInZapi = false WHERE c.webInstance.id = :webInstanceId")
    void deactivateAllChatsByWebInstanceId(@Param("webInstanceId") String webInstanceId);

    /**
     * Buscar chats não lidos ATIVOS de uma instância
     * Considera apenas chats com active_in_zapi = true
     */
    @Query("SELECT c FROM Chat c WHERE c.webInstance.id = :webInstanceId AND c.unread > 0 AND c.activeInZapi = true")
    List<Chat> findActiveUnreadChatsByWebInstanceId(@Param("webInstanceId") String webInstanceId);

    /**
     * Contar chats não lidos ATIVOS
     * Considera apenas chats com active_in_zapi = true
     */
    @Query("SELECT COUNT(c) FROM Chat c WHERE c.webInstance.id = :webInstanceId AND c.unread > 0 AND c.activeInZapi = true")
    Long countActiveUnreadByWebInstanceId(@Param("webInstanceId") String webInstanceId);

    /**
     * Buscar chats ativos por coluna
     * Filtra apenas chats com active_in_zapi = true
     */
    @Query("SELECT c FROM Chat c WHERE c.webInstance.id = :webInstanceId AND c.column = :column AND c.activeInZapi = true ORDER BY c.lastMessageTime DESC")
    List<Chat> findActiveChatsInColumn(@Param("webInstanceId") String webInstanceId, @Param("column") String column);
}