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
@Table(name = "documents", indexes = {
        @Index(name = "idx_document_chat_id", columnList = "chat_id"),
        @Index(name = "idx_document_message_id", columnList = "message_id"),
        @Index(name = "idx_document_timestamp", columnList = "timestamp")
})
public class Document {

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

    @Column(name = "document_url", nullable = false, columnDefinition = "TEXT")
    private String documentUrl;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "title")
    private String title;

    @Column(name = "caption", columnDefinition = "TEXT")
    private String caption;

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