package com.example.loginauthapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReplyDTO {
    private String id;
    private String messageId;
    private String referenceMessageId;
    private String messageContent;
    private String mensagemEnviada;
    private String senderName;
    private String audioUrl;
    private String documentUrl;
    private String imageUrl;
    private String videoUrl;
    private String replyType;
    private Boolean fromMe;
    private String timestamp;

    // ✅ NOVO: Flag indicando se a mensagem original foi encontrada
    private Boolean originalMessageNotFound;
}