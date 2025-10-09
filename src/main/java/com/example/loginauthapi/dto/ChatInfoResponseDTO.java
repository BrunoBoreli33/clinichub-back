package com.example.loginauthapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatInfoResponseDTO {
    private String id;
    private String name;
    private String phone;
    private LocalDateTime lastMessageTime;
    private Boolean isGroup;
    private Integer unread;
    private String profileThumbnail;
    private String column;

    // ✅ MODIFICADO: Agora retorna lista de tags ao invés de um único ticket
    private List<TagDTO> tags;
}