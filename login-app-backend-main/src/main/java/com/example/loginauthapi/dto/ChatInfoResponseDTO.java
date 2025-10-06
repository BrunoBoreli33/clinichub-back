package com.example.loginauthapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private String ticket;
}

