package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
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
    // MÉTODOS PARA CONTROLE DE active_in_zapi
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

    // ============================================
    // ✅ NOVOS MÉTODOS PARA ROTINAS AUTOMÁTICAS
    // ============================================

    /**
     * Buscar chats do usuário em colunas específicas
     * Usado para monitorar "Lead Quente" e "Atendimento Inicial"
     *
     * IMPORTANTE: Como Chat não tem userId direto, mas sim webInstance.user.id,
     * precisamos usar query JPQL customizada
     */
    @Query("SELECT c FROM Chat c WHERE c.webInstance.user.id = :userId AND c.column IN :columns")
    List<Chat> findByUserIdAndColumnIn(@Param("userId") String userId, @Param("columns") List<String> columns);

    /**
     * Buscar chats do usuário em uma coluna específica
     * Usado para buscar chats em "Repescagem"
     *
     * IMPORTANTE: Usa webInstance.user.id para relacionamento correto
     */
    @Query("SELECT c FROM Chat c WHERE c.webInstance.user.id = :userId AND c.column = :column")
    List<Chat> findByUserIdAndColumn(@Param("userId") String userId, @Param("column") String column);

    /**
     * Buscar chat por id, coluna e nao é grupo
     * Usado para filtrar chats para repescagem
     */
    @Query("SELECT c FROM Chat c WHERE c.webInstance.user.id = :userId AND c.column = :column AND c.isGroup = false")
    List<Chat> findByUserIdAndColumnAndNotGroup(
            @Param("userId") String userId,
            @Param("column") String column
    );


    /**
     * Buscar chat por phone e webInstanceId
     * Usado para uploads diretos e criação automática de chats
     */
    Optional<Chat> findByPhoneAndWebInstanceId(String phone, String webInstanceId);

    // ============================================
    // ✅ NOVOS MÉTODOS PARA SUPORTE A LID (WhatsApp Privacy)
    // ============================================

    /**
     * Buscar chat por chatLid e webInstanceId
     * Usado quando o webhook vem com chatLid (número oculto pelo WhatsApp)
     *
     * @param webInstanceId ID da instância do WhatsApp
     * @param chatLid Identificador LID do WhatsApp (ex: "36580504956936@lid")
     * @return Optional contendo o chat se encontrado
     */
    Optional<Chat> findByWebInstanceIdAndChatLid(String webInstanceId, String chatLid);

    /**
     * Buscar chat por chatLid OU phone
     * Usado para encontrar chat independente se o número foi revelado ou não
     * Prioriza busca por chatLid, mas também verifica phone
     *
     * @param webInstanceId ID da instância do WhatsApp
     * @param chatLid Identificador LID do WhatsApp
     * @param phone Número de telefone
     * @return Optional contendo o chat se encontrado
     */
    @Query("SELECT c FROM Chat c WHERE c.webInstance.id = :webInstanceId AND (c.chatLid = :chatLid OR c.phone = :phone)")
    Optional<Chat> findByWebInstanceIdAndChatLidOrPhone(
            @Param("webInstanceId") String webInstanceId,
            @Param("chatLid") String chatLid,
            @Param("phone") String phone
    );

    // ============================================
    // ✅ NOVOS MÉTODOS PARA DISPARO DE CAMPANHA
    // ============================================

    /**
     * Buscar todos os chats confiáveis de um usuário
     * Usado para disparo de campanhas com "todos os chats"
     */
    @Query("SELECT c FROM Chat c WHERE c.webInstance.user = :user AND c.isTrustworthy = true AND c.activeInZapi = true")
    List<Chat> findByWebInstance_UserAndIsTrustworthyTrue(@Param("user") User user);

    /**
     * Buscar chats confiáveis com tags específicas
     * Usado para disparo de campanhas filtradas por tags
     */
    @Query("SELECT DISTINCT c FROM Chat c JOIN c.tags t WHERE c.webInstance.user = :user AND t.id IN :tagIds AND c.isTrustworthy = true AND c.activeInZapi = true")
    List<Chat> findByWebInstance_UserAndTagsIdInAndIsTrustworthyTrue(
            @Param("user") User user,
            @Param("tagIds") List<String> tagIds
    );
}