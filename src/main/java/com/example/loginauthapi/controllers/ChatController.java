package com.example.loginauthapi.controllers;

import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import com.example.loginauthapi.services.RoutineAutomationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
    private final RoutineAutomationService routineAutomationService;

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

    /**
     * ✅ NOVO: PUT /dashboard/chats/{chatId}/mark-read
     * Marca o chat como lido (zera contador de mensagens não lidas)
     */
    @PutMapping("/{chatId}/mark-read")
    public ResponseEntity<Map<String, Object>> markChatAsRead(@PathVariable String chatId) {
        try {
            log.info("📖 Marcando chat {} como lido", chatId);

            User user = getAuthenticatedUser();
            WebInstance instance = getActiveInstance(user);

            Chat chat = chatRepository.findById(chatId)
                    .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

            // Verificar se o chat pertence ao usuário
            if (!chat.getWebInstance().getId().equals(instance.getId())) {
                log.warn("⚠️ Tentativa de acesso não autorizado ao chat {}", chatId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "message", "Chat não pertence ao usuário"
                ));
            }

            // ✅ Zerar contador de não lidas
            int previousUnread = chat.getUnread();
            chat.setUnread(0);
            chatRepository.save(chat);

            log.info("✅ Chat {} marcado como lido (unread: {} → 0)", chatId, previousUnread);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Chat marcado como lido",
                    "previousUnread", previousUnread,
                    "currentUnread", 0
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao marcar chat como lido: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Erro ao marcar chat como lido: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ NOVO: PATCH /dashboard/chats/{chatId}/reset-routine
     * Reseta o estado das rotinas automáticas de um chat
     * Zera last_routine_sent, lastAutomatedMessageSent e marca inRepescagem como false
     * Se o chat estiver em Repescagem, remove da coluna
     */
    @PatchMapping("/{chatId}/reset-routine")
    public ResponseEntity<Map<String, Object>> resetChatRoutine(@PathVariable String chatId) {
        try {
            log.info("🔄 Resetando rotinas do chat {}", chatId);

            User user = getAuthenticatedUser();
            WebInstance instance = getActiveInstance(user);

            // Busca o chat
            Chat chat = chatRepository.findById(chatId)
                    .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

            // Verifica se o chat pertence ao usuário
            if (!chat.getWebInstance().getId().equals(instance.getId())) {
                log.warn("⚠️ Tentativa de acesso não autorizado ao chat {}", chatId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "message", "Chat não pertence ao usuário"
                ));
            }

            // ✅ NOVA LÓGICA: Verifica se o chat está em Repescagem antes de resetar
            boolean wasInRepescagem = "followup".equals(chat.getColumn());

            // Chama o serviço de rotinas para resetar
            routineAutomationService.resetChatRoutineState(chatId);

            log.info("✅ Rotinas do chat {} resetadas pelo usuário {}", chatId, user.getEmail());

            // ✅ Se estava em Repescagem, informar ao usuário
            if (wasInRepescagem) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Rotinas resetadas e chat removido da Repescagem com sucesso"
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Rotinas resetadas com sucesso"
            ));

        } catch (Exception e) {
            log.error("❌ Erro ao resetar rotinas do chat {}", chatId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Erro ao resetar rotinas: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ NOVO: PATCH /dashboard/chats/{chatId}/toggle-hidden
     * Alterna o estado de oculto do chat (isHidden)
     */
    @PatchMapping("/{chatId}/toggle-hidden")
    public ResponseEntity<Map<String, Object>> toggleChatHidden(@PathVariable String chatId) {
        try {
            log.info("👁️ Alternando estado de oculto do chat {}", chatId);

            User user = getAuthenticatedUser();
            WebInstance instance = getActiveInstance(user);

            // Busca o chat
            Chat chat = chatRepository.findById(chatId)
                    .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

            // Verifica se o chat pertence ao usuário
            if (!chat.getWebInstance().getId().equals(instance.getId())) {
                log.warn("⚠️ Tentativa de acesso não autorizado ao chat {}", chatId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "message", "Chat não pertence ao usuário"
                ));
            }

            // Alterna o estado de isHidden
            boolean newHiddenState = !chat.getIsHidden();
            chat.setIsHidden(newHiddenState);
            chatRepository.save(chat);

            log.info("✅ Chat {} marcado como {} pelo usuário {}",
                    chatId,
                    newHiddenState ? "OCULTO" : "VISÍVEL",
                    user.getEmail());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", newHiddenState ? "Chat ocultado com sucesso" : "Chat exibido com sucesso",
                    "isHidden", newHiddenState
            ));

        } catch (Exception e) {
            log.error("❌ Erro ao alternar estado de oculto do chat {}", chatId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Erro ao alternar estado de oculto: " + e.getMessage()
            ));
        }
    }
}