package com.example.loginauthapi.controllers;

import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/dashboard/chats")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatRepository chatRepository;
    private final WebInstanceRepository webInstanceRepository;

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User)) {
            throw new RuntimeException("Usuário não autenticado");
        }
        return (User) auth.getPrincipal();
    }

    private WebInstance getActiveInstance(User user) {
        return webInstanceRepository.findByUserId(user.getId()).stream()
                .filter(i -> "ACTIVE".equalsIgnoreCase(i.getStatus()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Nenhuma instância ativa encontrada"));
    }

    /**
     * GET /dashboard/chats/{chatId}/check-updates?lastCheck=2024-01-01T10:00:00
     * Verifica se há novas mensagens desde lastCheck
     */
    @GetMapping("/{chatId}/check-updates")
    public ResponseEntity<Map<String, Object>> checkUpdates(
            @PathVariable String chatId,
            @RequestParam String lastCheck) {

        try {
            User user = getAuthenticatedUser();
            WebInstance instance = getActiveInstance(user);

            Chat chat = chatRepository.findById(chatId)
                    .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

            if (!chat.getWebInstance().getId().equals(instance.getId())) {
                return ResponseEntity.status(403).body(Map.of(
                        "hasNewMessages", false,
                        "error", "Chat não pertence ao usuário"
                ));
            }

            LocalDateTime lastCheckTime;
            try {
                // Suporta formato ISO 8601 com 'Z' (UTC)
                if (lastCheck.endsWith("Z")) {
                    lastCheckTime = java.time.Instant.parse(lastCheck)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime();
                } else {
                    lastCheckTime = LocalDateTime.parse(lastCheck);
                }
            } catch (Exception e) {
                log.warn("Erro ao parsear lastCheck: {}", lastCheck);
                return ResponseEntity.ok(Map.of("hasNewMessages", false));
            }

            boolean hasNewMessages = chat.getLastMessageTime() != null &&
                    chat.getLastMessageTime().isAfter(lastCheckTime);

            return ResponseEntity.ok(Map.of(
                    "hasNewMessages", hasNewMessages,
                    "lastMessageTime", chat.getLastMessageTime() != null ?
                            chat.getLastMessageTime().toString() : ""
            ));

        } catch (Exception e) {
            log.error("Erro ao verificar atualizações: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("hasNewMessages", false));
        }
    }
}