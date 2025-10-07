package com.example.loginauthapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para retornar informações de WebInstance
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebInstanceDTO {
    private String id;
    private String userId;
    private String userName;
    private String userEmail;
    private String status;
    private LocalDateTime criadoEm;
    private LocalDateTime expiraEm;
    private String clientToken;
    private String seuToken;
    private String suaInstancia;
    private Integer totalChats;
}

/**
 * DTO para criar uma nova WebInstance
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
class CreateWebInstanceDTO {
    private String userId;
    private String status;
    private String clientToken;
    private String seuToken;
    private String suaInstancia;
    private LocalDateTime expiraEm;
}

/**
 * DTO para atualizar uma WebInstance existente
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
class UpdateWebInstanceDTO {
    private String status;
    private String clientToken;
    private String seuToken;
    private String suaInstancia;
    private LocalDateTime expiraEm;
}