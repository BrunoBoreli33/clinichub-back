package com.example.loginauthapi.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CampaignDTO(
        String id,
        String name,
        String message,
        Integer chatsPerDispatch,
        Integer intervalMinutes,
        String status,
        Integer totalChats,
        Integer dispatchedChats,
        Double progressPercentage,
        LocalDateTime nextDispatchTime,
        List<String> tagIds,
        Boolean allTrustworthy,
        List<String> photoIds,  // ✅ NOVO: IDs das fotos a serem enviadas
        List<String> videoIds,  // ✅ NOVO: IDs dos vídeos a serem enviados
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {}