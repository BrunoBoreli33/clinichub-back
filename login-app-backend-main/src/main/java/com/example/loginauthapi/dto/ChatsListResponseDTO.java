package com.example.loginauthapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatsListResponseDTO {
    private boolean success;
    private String message;
    private Integer totalChats;
    private Integer unreadCount;
    private List<ChatInfoResponseDTO> chats;
}