package com.example.loginauthapi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDTO {
    private String id;
    private String messageId;
    private String instanceId;
    private String phone;
    private Boolean fromMe;
    private LocalDateTime timestamp;
    private String documentUrl;
    private String fileName;
    private String mimeType;
    private Integer pageCount;
    private String title;
    private String caption;
    private Boolean isStatusReply;
    private Boolean isEdit;
    private Boolean isGroup;
    private Boolean isNewsletter;
    private Boolean forwarded;
    private String chatName;
    private String senderName;
    private String status;
}