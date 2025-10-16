package com.example.loginauthapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class NotificationService {

    // Armazena emitters por userId
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    /**
     * Registrar um novo emitter para um usuário
     */
    public SseEmitter createEmitter(String userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // Timeout infinito

        // Adicionar emitter à lista do usuário
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        log.info("📡 Cliente SSE conectado - UserId: {}, Total de conexões: {}",
                userId, userEmitters.get(userId).size());

        // Remover emitter quando completar ou expirar
        emitter.onCompletion(() -> {
            removeEmitter(userId, emitter);
            log.info("✅ Cliente SSE desconectado (completion) - UserId: {}", userId);
        });

        emitter.onTimeout(() -> {
            removeEmitter(userId, emitter);
            log.info("⏱️ Cliente SSE desconectado (timeout) - UserId: {}", userId);
        });

        emitter.onError((ex) -> {
            removeEmitter(userId, emitter);
            log.error("❌ Erro no cliente SSE - UserId: {}, Erro: {}", userId, ex.getMessage());
        });

        // Enviar mensagem inicial de confirmação
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("message", "Conectado ao sistema de notificações"))
            );
        } catch (IOException e) {
            log.error("Erro ao enviar mensagem inicial", e);
            removeEmitter(userId, emitter);
        }

        return emitter;
    }

    /**
     * Remover emitter da lista
     */
    private void removeEmitter(String userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
    }

    /**
     * Enviar notificação de nova mensagem para um usuário específico
     */
    public void sendNewMessageNotification(String userId, Map<String, Object> messageData) {
        CopyOnWriteArrayList<SseEmitter> emitters = userEmitters.get(userId);

        if (emitters == null || emitters.isEmpty()) {
            log.debug("Nenhum cliente SSE conectado para o usuário: {}", userId);
            return;
        }

        log.info("📨 Enviando notificação para {} cliente(s) do usuário {}", emitters.size(), userId);

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("new-message")
                        .data(messageData)
                );
            } catch (IOException e) {
                log.error("Erro ao enviar notificação para cliente", e);
                removeEmitter(userId, emitter);
            }
        }
    }

    /**
     * Enviar notificação de atualização de chat
     */
    public void sendChatUpdateNotification(String userId, Map<String, Object> chatData) {
        CopyOnWriteArrayList<SseEmitter> emitters = userEmitters.get(userId);

        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("chat-update")
                        .data(chatData)
                );
            } catch (IOException e) {
                log.error("Erro ao enviar atualização de chat", e);
                removeEmitter(userId, emitter);
            }
        }
    }

    /**
     * ✅ NOVO: Enviar notificação de atualização de tag
     */
    public void sendTagUpdateNotification(String userId, Map<String, Object> tagData) {
        CopyOnWriteArrayList<SseEmitter> emitters = userEmitters.get(userId);

        if (emitters == null || emitters.isEmpty()) {
            log.debug("Nenhum cliente SSE conectado para o usuário: {}", userId);
            return;
        }

        log.info("🏷️ Enviando atualização de tag para {} cliente(s) do usuário {}", emitters.size(), userId);

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("tag-update")
                        .data(tagData)
                );
            } catch (IOException e) {
                log.error("Erro ao enviar atualização de tag", e);
                removeEmitter(userId, emitter);
            }
        }
    }

    /**
     * ✅ NOVO: Enviar notificação de exclusão de tag
     */
    public void sendTagDeleteNotification(String userId, Map<String, Object> tagData) {
        CopyOnWriteArrayList<SseEmitter> emitters = userEmitters.get(userId);

        if (emitters == null || emitters.isEmpty()) {
            log.debug("Nenhum cliente SSE conectado para o usuário: {}", userId);
            return;
        }

        log.info("🗑️ Enviando notificação de exclusão de tag para {} cliente(s) do usuário {}", emitters.size(), userId);

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("tag-delete")
                        .data(tagData)
                );
            } catch (IOException e) {
                log.error("Erro ao enviar notificação de exclusão de tag", e);
                removeEmitter(userId, emitter);
            }
        }
    }
}