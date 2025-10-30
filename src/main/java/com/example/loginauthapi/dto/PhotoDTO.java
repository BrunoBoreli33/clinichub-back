package com.example.loginauthapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoDTO {
    private String id;
    private String messageId;
    private String instanceId;
    private String phone;
    private Boolean fromMe;
    private String timestamp;
    private String imageUrl;
    private Integer width;
    private Integer height;
    private String mimeType;

    // ✅ NOVO: Campo caption para armazenar comentário da foto
    private String caption;

    private Boolean isStatusReply;
    private Boolean isEdit;
    private Boolean isGroup;
    private Boolean isNewsletter;
    private Boolean forwarded;
    private String chatName;
    private String senderName;
    private String status;
    private Boolean savedInGallery;
}