package com.example.loginauthapi.controllers;

import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Endpoint SSE para receber notificações em tempo real
     * GET /api/notifications/stream
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamNotifications() {
        try {
            // Obter usuário autenticado
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || !auth.isAuthenticated()) {
                log.error("❌ Usuário não autenticado tentando conectar ao SSE");
                return ResponseEntity.status(401).build();
            }

            if (!(auth.getPrincipal() instanceof User)) {
                log.error("❌ Principal não é um User");
                return ResponseEntity.status(401).build();
            }

            User user = (User) auth.getPrincipal();
            log.info("🔔 Nova conexão SSE - Usuário: {}", user.getEmail());

            // Criar e retornar emitter
            SseEmitter emitter = notificationService.createEmitter(user.getId());
            return ResponseEntity.ok(emitter);

        } catch (Exception e) {
            log.error("❌ Erro ao criar stream SSE", e);
            return ResponseEntity.status(500).build();
        }
    }
}