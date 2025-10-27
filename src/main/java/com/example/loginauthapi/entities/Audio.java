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
@Table(name = "audios", indexes = {
        @Index(name = "idx_audio_chat_id", columnList = "chat_id"),
        @Index(name = "idx_audio_message_id", columnList = "message_id"),
        @Index(name = "idx_audio_timestamp", columnList = "timestamp")
})
public class Audio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @Column(name = "message_id", nullable = false, unique = true)
    private String messageId;

    @Column(name = "instance_id", nullable = false)
    private String instanceId;

    @Column(name = "connected_phone", nullable = false)
    private String connectedPhone;

    @Column(nullable = false)
    private String phone;

    @Column(name = "from_me", nullable = false)
    private Boolean fromMe;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private Integer seconds;

    @Column(name = "audio_url", nullable = false, columnDefinition = "TEXT")
    private String audioUrl;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "view_once")
    private Boolean viewOnce = false;

    @Column(name = "is_status_reply")
    private Boolean isStatusReply = false;

    @Column(name = "sender_name")
    private String senderName;

    @Column(name = "sender_photo", columnDefinition = "TEXT")
    private String senderPhoto;

    @Column(length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}