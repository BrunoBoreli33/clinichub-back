package com.example.loginauthapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private String id;
    private String messageId;
    private String content;
    private String type; // ✅ NOVO
    private String audioUrl; // ✅ NOVO
    private Integer audioDuration; // ✅ NOVO
    private Boolean fromMe;
    private String timestamp;
    private String status;
    private String senderName;
    private String senderPhoto;
    private Boolean isEdited;
    private Boolean isForwarded;
}