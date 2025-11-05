package com.example.loginauthapi.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chats", indexes = {
        @Index(name = "idx_web_instance_id", columnList = "web_instance_id"),
        @Index(name = "idx_phone", columnList = "phone"),
        @Index(name = "idx_last_message_time", columnList = "last_message_time"),
        @Index(name = "idx_active_in_zapi", columnList = "active_in_zapi")
},
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"web_instance_id", "phone"})
        }
)
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "web_instance_id", nullable = false)
    private WebInstance webInstance;

    private String name;

    @Column(nullable = false, unique = false)
    private String phone;

    @Column(name = "last_message_time")
    private LocalDateTime lastMessageTime;

    // ✅ Conteúdo da última mensagem enviada/recebida
    @Column(name = "last_message_content", columnDefinition = "TEXT")
    private String lastMessageContent;

    @Column(name = "is_group", nullable = false)
    private Boolean isGroup = false;

    // ✅ IMPORTANTE: Campo exclusivo do sistema de notificações
    // NUNCA deve ser sobrescrito com dados do Z-API
    @Column(nullable = false)
    private Integer unread = 0;

    @Column(name = "profile_thumbnail", columnDefinition = "TEXT")
    private String profileThumbnail;

    @Column(name = "column_name")
    private String column;

    // ✅ NOVO CAMPO: Armazena a coluna anterior antes de ir para "tarefa"
    @Column(name = "previous_column")
    private String previousColumn;

    // ✅ NOVO CAMPO: Controla se o chat está ativo na instância Z-API atual
    // Permite isolar chats por instância sem perder histórico
    @Column(name = "active_in_zapi", nullable = false)
    private Boolean activeInZapi = true;

    // ✅ NOVO: Indica se este chat é usado para upload de mídias
    @Column(name = "is_upload_chat", nullable = false)
    private Boolean isUploadChat = false;

    // ✅ ALTERADO: De @OneToOne para @OneToMany
    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "chat_tags",
            joinColumns = @JoinColumn(name = "chat_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    // ✅ NOVO: Relacionamento com Messages - necessário para cascade delete
    // Quando um Chat é deletado, todas as suas Messages são deletadas automaticamente
    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Message> messages = new ArrayList<>();

    // ✅ NOVO: Relacionamento com ChatRoutineState - necessário para cascade delete
    // Quando um Chat é deletado, todos os seus ChatRoutineStates são deletados automaticamente
    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatRoutineState> routineStates = new ArrayList<>();

    // ✅ NOVO: Relacionamento com Audio - necessário para cascade delete
    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Audio> audios = new ArrayList<>();

    // ✅ NOVO: Relacionamento com Photo - necessário para cascade delete
    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Photo> photos = new ArrayList<>();

    // ✅ NOVO: Relacionamento com Video - necessário para cascade delete
    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Video> videos = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();

        // ✅ Garantir que activeInZapi nunca seja null
        if (this.activeInZapi == null) {
            this.activeInZapi = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    // Métodos auxiliares para gerenciar tags
    public void addTag(Tag tag) {
        this.tags.add(tag);
        tag.getChats().add(this);
    }

    public void removeTag(Tag tag) {
        this.tags.remove(tag);
        tag.getChats().remove(this);
    }

    public void clearTags() {
        for (Tag tag : new HashSet<>(this.tags)) {
            removeTag(tag);
        }
    }
}