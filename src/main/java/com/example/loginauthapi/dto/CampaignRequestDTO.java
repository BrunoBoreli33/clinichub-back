package com.example.loginauthapi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CampaignRequestDTO(
        @NotBlank(message = "Nome da campanha é obrigatório")
        @Size(max = 255, message = "Nome deve ter no máximo 255 caracteres")
        String name,

        @NotBlank(message = "Mensagem é obrigatória")
        @Size(max = 5000, message = "Mensagem deve ter no máximo 5000 caracteres")
        String message,

        @NotNull(message = "Chats por disparo é obrigatório")
        @Min(value = 1, message = "Chats por disparo deve ser no mínimo 1")
        Integer chatsPerDispatch,

        @NotNull(message = "Intervalo entre disparos é obrigatório")
        @Min(value = 1, message = "Intervalo deve ser no mínimo 1 minuto")
        Integer intervalMinutes,

        List<String> tagIds,

        Boolean allTrustworthy
) {}