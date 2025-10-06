package com.example.loginauthapi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponseDTO {

    // Informações do usuário
    private UserInfoDTO user;

    // Aqui você adiciona listas de dados de outras tabelas relacionadas ao usuário
    // Exemplo: se você tem tabelas de mensagens, contatos, conversas, etc.
    // private List<MessageDTO> messages;
    // private List<ContactDTO> contacts;
    // private List<ConversationDTO> conversations;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfoDTO {
        private String id;
        private String name;
        private String email;
        private String role;
        private boolean confirmed;
    }

    // Exemplo de DTOs para outras entidades (crie conforme suas tabelas)
    /*
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageDTO {
        private String id;
        private String content;
        private String sender;
        private LocalDateTime timestamp;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContactDTO {
        private String id;
        private String name;
        private String phone;
        private String tag;
    }
    */
}