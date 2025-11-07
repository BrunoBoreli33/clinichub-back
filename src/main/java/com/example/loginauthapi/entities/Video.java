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
@Table(name = "videos", indexes = {
        @Index(name = "idx_video_chat_id", columnList = "chat_id"),
        @Index(name = "idx_video_message_id", columnList = "message_id"),
        @Index(name = "idx_video_timestamp", columnList = "timestamp"),
        @Index(name = "idx_video_saved_in_gallery", columnList = "saved_in_gallery")
})
public class Video {

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

    @Column(nullable = false)
    private String phone;

    @Column(name = "from_me", nullable = false)
    private Boolean fromMe;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "video_url", nullable = false, columnDefinition = "TEXT")
    private String videoUrl;

    @Column(columnDefinition = "TEXT")
    private String caption;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    @Column(nullable = false)
    private Integer seconds;

    @Column(name = "view_once")
    private Boolean viewOnce = false;

    @Column(name = "is_gif")
    private Boolean isGif = false;

    @Column(name = "is_status_reply")
    private Boolean isStatusReply = false;

    @Column(name = "is_edit")
    private Boolean isEdit = false;

    @Column(name = "is_group")
    private Boolean isGroup = false;

    @Column(name = "is_newsletter")
    private Boolean isNewsletter = false;

    @Column(name = "forwarded")
    private Boolean forwarded = false;

    @Column(name = "chat_name")
    private String chatName;

    @Column(name = "sender_name")
    private String senderName;

    @Column(length = 20)
    private String status;

    @Column(name = "saved_in_gallery", nullable = false)
    private Boolean savedInGallery = false;

    // ✅ NOVO: Flag para soft delete - vídeo deletado do chat mas mantido na galeria
    @Column(name = "deleted_from_chat", nullable = false)
    private Boolean deletedFromChat = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.deletedFromChat == null) {
            this.deletedFromChat = false;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}