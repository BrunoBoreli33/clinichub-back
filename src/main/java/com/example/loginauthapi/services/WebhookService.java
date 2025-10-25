package com.example.loginauthapi.services;

import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final MessageService messageService;
    private final AudioService audioService; // ✅ ADICIONAR ESTA LINHA
    private final ChatRepository chatRepository;
    private final WebInstanceRepository webInstanceRepository;
    private final NotificationService notificationService;

    /**
     * ✅ MODIFICADO: Método unificado para processar QUALQUER mensagem
     * - Reseta contador quando fromMe=true (mensagem enviada fora do sistema)
     * - Incrementa contador quando fromMe=false (mensagem recebida)
     * - Atualiza lastMessageContent SEMPRE
     * - Emite notificações SSE SEMPRE (new-message para recebidas, chat-update para enviadas)
     */
    @Transactional
    public void processMessage(Map<String, Object> payload) {
        try {
            log.info("📄 Processando mensagem unificada");

            // ===== EXTRAIR INFORMAÇÕES IMPORTANTES =====
            Boolean fromMe = (Boolean) payload.get("fromMe");
            Long momment = payload.get("momment") != null ?
                    ((Number) payload.get("momment")).longValue() : System.currentTimeMillis();

            String connectedPhone = (String) payload.get("connectedPhone");
            String phone = (String) payload.get("phone");
            String instanceId = (String) payload.get("instanceId");
            String messageId = (String) payload.get("messageId");
            String chatName = (String) payload.get("chatName");
            String senderName = (String) payload.get("senderName");
            String status = (String) payload.get("status");
            String senderPhoto = (String) payload.get("senderPhoto");
            Boolean isForwarded = (Boolean) payload.get("forwarded");
            Boolean isGroup = (Boolean) payload.get("isGroup");

            // ✅ NOVO: Verificar se é áudio
            @SuppressWarnings("unchecked")
            Map<String, Object> audioObj = (Map<String, Object>) payload.get("audio");

            if (audioObj != null) {
                // ✅ PROCESSAR ÁUDIO
                processAudio(payload, audioObj, fromMe, momment, connectedPhone, phone,
                        instanceId, messageId, chatName, senderName, status,
                        senderPhoto, isForwarded, isGroup);
                return;
            }

            // ===== PROCESSAR TEXTO (CÓDIGO ORIGINAL MANTIDO) =====
            // Extrair conteúdo da mensagem
            @SuppressWarnings("unchecked")
            Map<String, Object> textObj = (Map<String, Object>) payload.get("text");
            String content = textObj != null ? (String) textObj.get("message") : "";

            if (content == null || content.trim().isEmpty()) {
                log.warn("⚠️ Mensagem vazia recebida, ignorando");
                return;
            }

            log.info("🔍 Mensagem extraída - FromMe: {}, Phone: {}, InstanceId: {}, Content: {}",
                    fromMe, phone, instanceId, content);

            // ===== BUSCAR INSTÂNCIA POR INSTANCE ID =====
            Optional<WebInstance> instanceOpt = Optional.empty();

            if (instanceId != null && !instanceId.trim().isEmpty()) {
                instanceOpt = webInstanceRepository.findBySuaInstancia(instanceId);
            }

            if (instanceOpt.isEmpty() && connectedPhone != null) {
                instanceOpt = webInstanceRepository.findByConnectedPhone(connectedPhone);
            }

            if (instanceOpt.isEmpty()) {
                log.warn("⚠️ WebInstance não encontrada - InstanceId: {}, ConnectedPhone: {}",
                        instanceId, connectedPhone);
                return;
            }

            WebInstance instance = instanceOpt.get();
            log.info("✅ Instância encontrada - ID: {}, User: {}",
                    instance.getId(), instance.getUser().getEmail());

            // ===== BUSCAR OU CRIAR CHAT =====
            Optional<Chat> chatOpt = chatRepository.findByWebInstanceIdAndPhone(instance.getId(), phone);
            Chat chat;
            boolean isNewChat = false;

            if (chatOpt.isEmpty()) {
                // ✅ CRIAR NOVO CHAT
                chat = new Chat();
                chat.setWebInstance(instance);
                chat.setPhone(phone);
                chat.setName(chatName != null ? chatName : phone);
                chat.setIsGroup(isGroup != null ? isGroup : false);
                chat.setUnread(fromMe ? 0 : 1);
                chat.setColumn("inbox");
                chat.setProfileThumbnail(senderPhoto);

                // ✅ NOVO: Definir lastMessageContent
                chat.setLastMessageContent(truncateMessage(content, 50));

                chat = chatRepository.save(chat);
                isNewChat = true;

                log.info("✅ Novo chat criado - ID: {}, Nome: {}, Phone: {}, Unread: {}, LastMessage: '{}'",
                        chat.getId(), chat.getName(), chat.getPhone(), chat.getUnread(),
                        chat.getLastMessageContent());

            } else {
                // ✅ ATUALIZAR CHAT EXISTENTE
                chat = chatOpt.get();
                int previousUnread = chat.getUnread();

                // Atualizar nome se mudou
                if (chatName != null && !chatName.equals(chat.getName())) {
                    chat.setName(chatName);
                }

                // Atualizar foto de perfil se disponível
                if (senderPhoto != null && !senderPhoto.isEmpty()) {
                    chat.setProfileThumbnail(senderPhoto);
                }

                // ✅ ATUALIZAR lastMessageContent SEMPRE
                chat.setLastMessageContent(truncateMessage(content, 50));

                // ✅ LÓGICA DE CONTADOR baseada em fromMe
                if (fromMe) {
                    // Mensagem ENVIADA → ZERAR contador
                    chat.setUnread(0);
                    log.info("📤 Mensagem enviada detectada - Resetando contador (unread: {} → 0)",
                            previousUnread);
                } else {
                    // Mensagem RECEBIDA → INCREMENTAR contador
                    chat.setUnread(chat.getUnread() + 1);
                    log.info("📥 Mensagem recebida - Incrementando contador (unread: {} → {})",
                            previousUnread, chat.getUnread());
                }

                chat = chatRepository.save(chat);
                log.info("✅ Chat atualizado - ID: {}, Unread: {}, LastMessage: '{}'",
                        chat.getId(), chat.getUnread(), chat.getLastMessageContent());
            }

            // ===== SALVAR MENSAGEM =====
            messageService.saveIncomingMessage(
                    chat.getId(),
                    messageId,
                    content,
                    fromMe,
                    momment,
                    status,
                    senderName,
                    senderPhoto,
                    isForwarded != null ? isForwarded : false,
                    isGroup != null ? isGroup : false
            );

            // ✅ ATUALIZAR lastMessageTime do chat
            chat.setLastMessageTime(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(momment),
                    java.time.ZoneId.systemDefault()
            ));
            chat = chatRepository.save(chat);

            log.info("✅ Mensagem processada com sucesso - MessageId: {}, Chat: {}, FromMe: {}",
                    messageId, chat.getId(), fromMe);

            // ===== ✅ ENVIAR NOTIFICAÇÃO SSE SEMPRE =====
            if (!fromMe) {
                // Mensagem RECEBIDA: Enviar notificação completa com som
                sendNotificationToUser(instance.getUser().getId(), chat, content, isNewChat);
            } else {
                // Mensagem ENVIADA: Enviar atualização de chat (sem som)
                sendChatUpdateToUser(instance.getUser().getId(), chat);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao processar mensagem", e);
            throw new RuntimeException("Erro ao processar webhook: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ NOVO: Processar mensagem de áudio
     */
    private void processAudio(Map<String, Object> payload, Map<String, Object> audioObj,
                              Boolean fromMe, Long momment, String connectedPhone, String phone,
                              String instanceId, String messageId, String chatName, String senderName,
                              String status, String senderPhoto, Boolean isForwarded, Boolean isGroup) {
        try {
            String audioUrl = (String) audioObj.get("audioUrl");
            Integer seconds = audioObj.get("seconds") != null ?
                    ((Number) audioObj.get("seconds")).intValue() : 0;
            String mimeType = (String) audioObj.get("mimeType");
            Boolean viewOnce = (Boolean) audioObj.get("viewOnce");
            Boolean isStatusReply = (Boolean) payload.get("isStatusReply");

            log.info("🎤 Áudio - URL: {}, Duração: {}s", audioUrl, seconds);

            // ===== BUSCAR INSTÂNCIA =====
            Optional<WebInstance> instanceOpt = Optional.empty();

            if (instanceId != null && !instanceId.trim().isEmpty()) {
                instanceOpt = webInstanceRepository.findBySuaInstancia(instanceId);
            }

            if (instanceOpt.isEmpty() && connectedPhone != null) {
                instanceOpt = webInstanceRepository.findByConnectedPhone(connectedPhone);
            }

            if (instanceOpt.isEmpty()) {
                log.warn("⚠️ WebInstance não encontrada para áudio");
                return;
            }

            WebInstance instance = instanceOpt.get();

            // ===== BUSCAR OU CRIAR CHAT =====
            Optional<Chat> chatOpt = chatRepository.findByWebInstanceIdAndPhone(instance.getId(), phone);
            Chat chat;
            boolean isNewChat = false;

            if (chatOpt.isEmpty()) {
                chat = new Chat();
                chat.setWebInstance(instance);
                chat.setPhone(phone);
                chat.setName(chatName != null ? chatName : phone);
                chat.setIsGroup(isGroup != null ? isGroup : false);
                chat.setUnread(fromMe ? 0 : 1);
                chat.setColumn("inbox");
                chat.setProfileThumbnail(senderPhoto);
                chat.setLastMessageContent("🎤 Áudio");
                chat = chatRepository.save(chat);
                isNewChat = true;
                log.info("✅ Novo chat criado para áudio - ID: {}", chat.getId());
            } else {
                chat = chatOpt.get();
                int previousUnread = chat.getUnread();

                if (chatName != null && !chatName.equals(chat.getName())) {
                    chat.setName(chatName);
                }

                if (senderPhoto != null && !senderPhoto.isEmpty()) {
                    chat.setProfileThumbnail(senderPhoto);
                }

                chat.setLastMessageContent("🎤 Áudio");

                if (fromMe) {
                    chat.setUnread(0);
                    log.info("📤 Áudio enviado - Resetando contador (unread: {} → 0)", previousUnread);
                } else {
                    chat.setUnread(chat.getUnread() + 1);
                    log.info("📥 Áudio recebido - Incrementando contador (unread: {} → {})",
                            previousUnread, chat.getUnread());
                }

                chat = chatRepository.save(chat);
            }

            // ===== SALVAR ÁUDIO =====
            audioService.saveIncomingAudio(
                    chat.getId(), messageId, instanceId, connectedPhone, phone, fromMe,
                    momment, seconds, audioUrl, mimeType, viewOnce, isStatusReply,
                    senderName, senderPhoto, status
            );

            // ===== ATUALIZAR lastMessageTime =====
            chat.setLastMessageTime(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(momment),
                    java.time.ZoneId.systemDefault()
            ));
            chat = chatRepository.save(chat);

            log.info("✅ Áudio processado com sucesso - MessageId: {}, Chat: {}", messageId, chat.getId());

            // ===== ENVIAR NOTIFICAÇÃO SSE =====
            if (!fromMe) {
                sendNotificationToUser(instance.getUser().getId(), chat, "🎤 Áudio", isNewChat);
            } else {
                sendChatUpdateToUser(instance.getUser().getId(), chat);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao processar áudio", e);
        }
    }

    /**
     * ✅ NOVO: Truncar mensagem para exibição
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "";
        if (message.length() <= maxLength) return message;
        return message.substring(0, maxLength) + "...";
    }

    /**
     * ✅ Enviar notificação SSE para mensagens RECEBIDAS (new-message)
     */
    private void sendNotificationToUser(String userId, Chat chat, String messageContent, boolean isNewChat) {
        try {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("chatId", chat.getId());
            notificationData.put("chatName", chat.getName());
            notificationData.put("chatPhone", chat.getPhone());
            notificationData.put("message", messageContent);
            notificationData.put("lastMessageContent", chat.getLastMessageContent()); // ✅ NOVO
            notificationData.put("unreadCount", chat.getUnread());
            notificationData.put("isNewChat", isNewChat);
            notificationData.put("profileThumbnail", chat.getProfileThumbnail());
            notificationData.put("lastMessageTime", chat.getLastMessageTime() != null ?
                    chat.getLastMessageTime().toString() : null);
            notificationData.put("column", chat.getColumn());
            notificationData.put("isGroup", chat.getIsGroup());

            notificationService.sendNewMessageNotification(userId, notificationData);
            log.info("📢 Notificação SSE enviada para usuário: {} (chat: {}, unread: {}, lastMessage: '{}')",
                    userId, chat.getId(), chat.getUnread(), chat.getLastMessageContent());

        } catch (Exception e) {
            log.error("❌ Erro ao enviar notificação SSE", e);
        }
    }

    /**
     * ✅ Enviar atualização de chat para mensagens ENVIADAS (chat-update)
     */
    private void sendChatUpdateToUser(String userId, Chat chat) {
        try {
            Map<String, Object> chatData = new HashMap<>();
            chatData.put("chatId", chat.getId());
            chatData.put("chatName", chat.getName());
            chatData.put("chatPhone", chat.getPhone());
            chatData.put("lastMessageContent", chat.getLastMessageContent()); // ✅ NOVO
            chatData.put("unreadCount", chat.getUnread());
            chatData.put("profileThumbnail", chat.getProfileThumbnail());
            chatData.put("lastMessageTime", chat.getLastMessageTime() != null ?
                    chat.getLastMessageTime().toString() : null);
            chatData.put("column", chat.getColumn());
            chatData.put("isGroup", chat.getIsGroup());

            notificationService.sendChatUpdateNotification(userId, chatData);
            log.info("🔄 Atualização de chat enviada via SSE para usuário: {} (chat: {}, lastMessage: '{}')",
                    userId, chat.getId(), chat.getLastMessageContent());

        } catch (Exception e) {
            log.error("❌ Erro ao enviar atualização de chat via SSE", e);
        }
    }

    /**
     * ⚠️ MANTIDO PARA COMPATIBILIDADE: Métodos antigos ainda funcionam
     */
    @Transactional
    public void processIncomingMessage(Map<String, Object> payload) {
        processMessage(payload);
    }

    @Transactional
    public void processOutcomingMessage(Map<String, Object> payload) {
        processMessage(payload);
    }
}