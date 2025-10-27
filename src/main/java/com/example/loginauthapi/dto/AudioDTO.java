package com.example.loginauthapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioDTO {
    private String id;
    private String messageId;
    private String instanceId;
    private String connectedPhone;
    private String phone;
    private Boolean fromMe;
    private String timestamp;
    private Integer seconds;
    private String audioUrl;
    private String mimeType;
    private Boolean viewOnce;
    private Boolean isStatusReply;
    private String senderName;
    private String senderPhoto;
    private String status;
}