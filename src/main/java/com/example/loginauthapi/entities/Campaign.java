package com.example.loginauthapi.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "campaigns", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_next_dispatch_time", columnList = "next_dispatch_time")
})
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "chats_per_dispatch", nullable = false)
    private Integer chatsPerDispatch;

    @Column(name = "interval_minutes", nullable = false)
    private Integer intervalMinutes;

    @Column(name = "status", nullable = false)
    private String status = "CRIADA"; // CRIADA, EM_ANDAMENTO, PAUSADA, CONCLUIDA, CANCELADA

    @Column(name = "total_chats", nullable = false)
    private Integer totalChats = 0;

    @Column(name = "dispatched_chats", nullable = false)
    private Integer dispatchedChats = 0;

    @Column(name = "next_dispatch_time")
    private LocalDateTime nextDispatchTime;

    // Armazena os IDs dos tags selecionados separados por vírgula
    @Column(name = "tag_ids", columnDefinition = "TEXT")
    private String tagIds;

    // Se true, considera todos os chats com is_trustworthy=true
    @Column(name = "all_trustworthy", nullable = false)
    private Boolean allTrustworthy = false;

    // ✅ NOVO: Armazena os IDs das fotos da galeria a serem enviadas (separados por vírgula)
    @Column(name = "photo_ids", columnDefinition = "TEXT")
    private String photoIds;

    // ✅ NOVO: Armazena os IDs dos vídeos da galeria a serem enviados (separados por vírgula)
    @Column(name = "video_ids", columnDefinition = "TEXT")
    private String videoIds;

    // Armazena os IDs dos chats que já receberam a mensagem
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "campaign_dispatched_chats",
            joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "chat_id")
    private Set<String> dispatchedChatIds = new HashSet<>();

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
        // ✅ CRÍTICO: Sempre sincronizar dispatched_chats antes de salvar
        syncDispatchedChats();
    }

    @PostLoad
    public void postLoad() {
        // ✅ CRÍTICO: Sempre sincronizar dispatched_chats após carregar do banco
        syncDispatchedChats();
    }

    /**
     * ✅ NOVO: Sincroniza o campo dispatched_chats com o tamanho real da lista
     * Este método garante que o contador sempre reflita a realidade
     */
    public void syncDispatchedChats() {
        if (this.dispatchedChatIds != null) {
            this.dispatchedChats = this.dispatchedChatIds.size();
        }
    }

    /**
     * ✅ MODIFICADO: Getter que sempre retorna o tamanho real da lista
     * Ignora o valor do campo e calcula em tempo real
     */
    public Integer getDispatchedChats() {
        if (this.dispatchedChatIds != null) {
            return this.dispatchedChatIds.size();
        }
        return this.dispatchedChats;
    }

    // Método auxiliar para calcular a porcentagem de conclusão
    public Double getProgressPercentage() {
        if (totalChats == 0) return 0.0;
        return (getDispatchedChats().doubleValue() / totalChats.doubleValue()) * 100.0;
    }
}