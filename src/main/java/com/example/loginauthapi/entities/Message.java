package com.example.loginauthapi.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_chat_id", columnList = "chat_id"),
        @Index(name = "idx_message_id", columnList = "message_id"),
        @Index(name = "idx_timestamp", columnList = "timestamp")
})
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @Column(name = "message_id", nullable = false, unique = true)
    private String messageId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // ✅ NOVO: Tipo da mensagem (text, audio, image, etc)
    @Column(nullable = false, length = 20)
    private String type = "text"; // text, audio, image, video, document

    // ✅ NOVO: URL do áudio (se for mensagem de áudio)
    @Column(name = "audio_url", columnDefinition = "TEXT")
    private String audioUrl;

    // ✅ NOVO: Duração do áudio em segundos
    @Column(name = "audio_duration")
    private Integer audioDuration;

    @Column(nullable = false)
    private Boolean fromMe;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(length = 20)
    private String status;

    @Column(name = "sender_name")
    private String senderName;

    @Column(name = "sender_photo", columnDefinition = "TEXT")
    private String senderPhoto;

    @Column(name = "is_edited")
    private Boolean isEdited = false;

    @Column(name = "is_forwarded")
    private Boolean isForwarded = false;

    @Column(name = "is_group")
    private Boolean isGroup = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.type == null) {
            this.type = "text";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}