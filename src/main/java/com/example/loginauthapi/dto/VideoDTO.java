package com.example.loginauthapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoDTO {
    private String id;
    private String messageId;
    private String instanceId;
    private String phone;
    private Boolean fromMe;
    private String timestamp;
    private String videoUrl;
    private String caption;
    private String mimeType;
    private Integer width;
    private Integer height;
    private Integer seconds;
    private Boolean viewOnce;
    private Boolean isGif;
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