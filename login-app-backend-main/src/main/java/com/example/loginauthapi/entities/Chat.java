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
@Table(name = "chats", indexes = {
        @Index(name = "idx_web_instance_id", columnList = "web_instance_id"),
        @Index(name = "idx_phone", columnList = "phone"),
        @Index(name = "idx_last_message_time", columnList = "last_message_time")
})
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "web_instance_id", nullable = false)
    private WebInstance webInstance;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = false) // phone não é globalmente único, apenas por instância
    private String phone;

    @Column(name = "last_message_time")
    private LocalDateTime lastMessageTime;

    @Column(name = "is_group", nullable = false)
    private Boolean isGroup = false;

    @Column(nullable = false)
    private Integer unread = 0;

    @Column(name = "profile_thumbnail", columnDefinition = "TEXT")
    private String profileThumbnail; // URL ou base64 da foto

    @Column(name = "column_name")
    private String column; // Coluna onde o chat está organizado (ex: "inbox", "archived", etc)

    // ✅ NOVO: Relacionamento ManyToMany com Tags
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

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    // ✅ MÉTODOS AUXILIARES PARA GERENCIAR TAGS
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

    // Constraint única composta: um phone por instância
    @Table(uniqueConstraints = {
            @UniqueConstraint(columnNames = {"web_instance_id", "phone"})
    })
    public static class ChatUniqueConstraint {}
}