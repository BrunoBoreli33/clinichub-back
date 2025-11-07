package com.example.loginauthapi.services;

import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.ChatRoutineState;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.ChatRoutineStateRepository;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import com.example.loginauthapi.repositories.PhotoRepository;
import com.example.loginauthapi.repositories.VideoRepository;
import com.example.loginauthapi.repositories.DocumentRepository;
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
    private final AudioService audioService;
    private final PhotoService photoService;
    private final VideoService videoService;
    private final DocumentService documentService;
    private final ChatRepository chatRepository;
    private final WebInstanceRepository webInstanceRepository;
    private final NotificationService notificationService;

    // ✅ NOVO: Injeções necessárias para remover chats da repescagem
    private final ChatRoutineStateRepository chatRoutineStateRepository;
    private final RoutineAutomationService routineAutomationService;

    private final PhotoRepository photoRepository;
    private final VideoRepository videoRepository;
    private final DocumentRepository documentRepository;

    // Constante para identificar a coluna de repescagem
    private static final String REPESCAGEM_COLUMN = "followup";

    /**
     * ✅ MODIFICADO: Método unificado para processar QUALQUER mensagem
     * - Suporta texto, áudio, fotos e vídeos
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

            // ✅ NOVO: Verificar se é imagem
            @SuppressWarnings("unchecked")
            Map<String, Object> imageObj = (Map<String, Object>) payload.get("image");

            if (imageObj != null) {
                // ✅ PROCESSAR FOTO
                processPhoto(payload, imageObj, fromMe, momment, connectedPhone, phone,
                        instanceId, messageId, chatName, senderName, status,
                        senderPhoto, isForwarded, isGroup);
                return;
            }

            // ✅ Verificar se é áudio
            @SuppressWarnings("unchecked")
            Map<String, Object> audioObj = (Map<String, Object>) payload.get("audio");

            if (audioObj != null) {
                // ✅ PROCESSAR ÁUDIO
                processAudio(payload, audioObj, fromMe, momment, connectedPhone, phone,
                        instanceId, messageId, chatName, senderName, status,
                        senderPhoto, isForwarded, isGroup);
                return;
            }

            // ✅ NOVO: Verificar se é vídeo
            @SuppressWarnings("unchecked")
            Map<String, Object> videoObj = (Map<String, Object>) payload.get("video");

            if (videoObj != null) {
                // ✅ PROCESSAR VÍDEO
                processVideo(payload, videoObj, fromMe, momment, connectedPhone, phone,
                        instanceId, messageId, chatName, senderName, status,
                        senderPhoto, isForwarded, isGroup);
                return;
            }

            // ✅ NOVO: Verificar se é documento
            @SuppressWarnings("unchecked")
            Map<String, Object> documentObj = (Map<String, Object>) payload.get("document");

            if (documentObj != null) {
                // ✅ PROCESSAR DOCUMENTO
                processDocument(payload, documentObj, fromMe, momment, connectedPhone, phone,
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

            log.info("📝 Mensagem extraída - FromMe: {}, Phone: {}, InstanceId: {}, Content: {}",
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
                    // ✅ CORREÇÃO: Recarregar chat do banco para evitar race condition
                    chat = chatRepository.findById(chat.getId()).orElse(chat);
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

            // ✅ NOVO: VERIFICAR SE DEVE REMOVER DA REPESCAGEM
            checkAndRemoveFromRepescagem(chat, fromMe, instance);

            // ✅ ENVIAR NOTIFICAÇÃO SSE
            if (!fromMe) {
                sendNotificationToUser(instance.getUser().getId(), chat, content, isNewChat);
            } else {
                sendChatUpdateToUser(instance.getUser().getId(), chat);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao processar mensagem", e);
        }
    }

    /**
     * ✅ NOVO: Processar foto recebida via webhook
     */
    @Transactional
    public void processPhoto(Map<String, Object> payload, Map<String, Object> imageObj,
                             Boolean fromMe, Long momment, String connectedPhone, String phone,
                             String instanceId, String messageId, String chatName, String senderName,
                             String status, String senderPhoto, Boolean isForwarded, Boolean isGroup) {
        try {
            log.info("📸 Processando foto do webhook");

            // ===== EXTRAIR DADOS DA FOTO =====
            String imageUrl = (String) imageObj.get("imageUrl");
            Integer width = imageObj.get("width") != null ? ((Number) imageObj.get("width")).intValue() : 0;
            Integer height = imageObj.get("height") != null ? ((Number) imageObj.get("height")).intValue() : 0;
            String mimeType = (String) imageObj.get("mimeType");
            String caption = (String) imageObj.get("caption"); // ✅ NOVO: Extrair caption
            Boolean isStatusReply = (Boolean) payload.get("isStatusReply");
            Boolean isEdit = (Boolean) payload.get("isEdit");
            Boolean isNewsletter = (Boolean) payload.get("isNewsletter");

            log.info("📸 Dados da foto - ImageUrl: {}, Width: {}, Height: {}, Caption: {}",
                    imageUrl != null ? "presente" : "null", width, height, caption);

            // ===== BUSCAR INSTÂNCIA =====
            Optional<WebInstance> instanceOpt = Optional.empty();

            if (instanceId != null && !instanceId.trim().isEmpty()) {
                instanceOpt = webInstanceRepository.findBySuaInstancia(instanceId);
            }

            if (instanceOpt.isEmpty() && connectedPhone != null) {
                instanceOpt = webInstanceRepository.findByConnectedPhone(connectedPhone);
            }

            if (instanceOpt.isEmpty()) {
                log.warn("⚠️ WebInstance não encontrada para foto");
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
                chat.setLastMessageContent("Foto 📸");
                chat = chatRepository.save(chat);
                isNewChat = true;
                log.info("✅ Novo chat criado para foto - ID: {}", chat.getId());
            } else {
                chat = chatOpt.get();
                int previousUnread = chat.getUnread();

                if (chatName != null && !chatName.equals(chat.getName())) {
                    chat.setName(chatName);
                }

                if (senderPhoto != null && !senderPhoto.isEmpty()) {
                    chat.setProfileThumbnail(senderPhoto);
                }

                chat.setLastMessageContent("Foto 📸");

                if (fromMe) {
                    chat.setUnread(0);
                    log.info("📤 Foto enviada - Resetando contador (unread: {} → 0)", previousUnread);
                } else {
                    // ✅ CORREÇÃO: Recarregar chat do banco para evitar race condition
                    chat = chatRepository.findById(chat.getId()).orElse(chat);
                    chat.setUnread(chat.getUnread() + 1);
                    log.info("📥 Foto recebida - Incrementando contador (unread: {} → {})",
                            previousUnread, chat.getUnread());
                }

                chat = chatRepository.save(chat);
            }

            // ===== SALVAR FOTO =====
            photoService.saveIncomingPhoto(
                    chat.getId(), messageId, instanceId, phone, fromMe, momment,
                    imageUrl, width, height, mimeType, caption, isStatusReply, isEdit,
                    isGroup, isNewsletter, isForwarded, chatName, senderName, status
            );

            // ✅ NOVO: VERIFICAR SE É UPLOAD E AUTO-SALVAR NA GALERIA
            User owner = instance.getUser();
            if (owner.getUploadPhoneNumber() != null &&
                    owner.getUploadPhoneNumber().equals(phone) &&
                    !fromMe) {

                log.info("📸 Foto recebida no número de upload - Auto-salvando na galeria");

                // Marcar chat como upload chat
                if (!chat.getIsUploadChat()) {
                    chat.setIsUploadChat(true);
                    chatRepository.save(chat);
                }

                // Buscar a foto recém-salva e marcar como salva na galeria
                photoRepository.findByMessageId(messageId).ifPresent(photo -> {
                    photo.setSavedInGallery(true);
                    photoRepository.save(photo);
                    log.info("✅ Foto auto-salva na galeria - PhotoId: {}", photo.getId());
                });
            }

            // ===== ATUALIZAR lastMessageTime =====
            chat.setLastMessageTime(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(momment),
                    java.time.ZoneId.systemDefault()
            ));
            chat = chatRepository.save(chat);

            log.info("✅ Foto processada com sucesso - MessageId: {}, Chat: {}", messageId, chat.getId());

            // ✅ NOVO: VERIFICAR SE DEVE REMOVER DA REPESCAGEM
            checkAndRemoveFromRepescagem(chat, fromMe, instance);

            // ===== ENVIAR NOTIFICAÇÃO SSE =====
            if (!fromMe) {
                sendNotificationToUser(instance.getUser().getId(), chat, chat.getLastMessageContent(), isNewChat);
            } else {
                sendChatUpdateToUser(instance.getUser().getId(), chat);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao processar foto", e);
        }
    }

    /**
     * ✅ Processar áudio recebido via webhook
     */
    @Transactional
    public void processAudio(Map<String, Object> payload, Map<String, Object> audioObj,
                             Boolean fromMe, Long momment, String connectedPhone, String phone,
                             String instanceId, String messageId, String chatName, String senderName,
                             String status, String senderPhoto, Boolean isForwarded, Boolean isGroup) {
        try {
            log.info("🎤 Processando áudio do webhook");

            // ===== EXTRAIR DADOS DO ÁUDIO =====
            Integer seconds = audioObj.get("seconds") != null ? ((Number) audioObj.get("seconds")).intValue() : 0;
            String audioUrl = (String) audioObj.get("audioUrl");
            String mimeType = (String) audioObj.get("mimeType");
            Boolean viewOnce = (Boolean) audioObj.get("viewOnce");
            Boolean isStatusReply = (Boolean) payload.get("isStatusReply");

            log.info("🎤 Dados do áudio - Seconds: {}, AudioUrl: {}", seconds, audioUrl != null ? "presente" : "null");

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
                chat.setLastMessageContent("Mensagem de Áudio");
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

                chat.setLastMessageContent("Mensagem de Áudio");

                if (fromMe) {
                    chat.setUnread(0);
                    log.info("📤 Áudio enviado - Resetando contador (unread: {} → 0)", previousUnread);
                } else {
                    // ✅ CORREÇÃO: Recarregar chat do banco para evitar race condition
                    chat = chatRepository.findById(chat.getId()).orElse(chat);
                    chat.setUnread(chat.getUnread() + 1);
                    log.info("📥 Áudio recebido - Incrementando contador (unread: {} → {})",
                            previousUnread, chat.getUnread());
                }

                chat = chatRepository.save(chat);
            }

            // ✅ NOVO: Garantir que senderName esteja correto
            String finalSenderName = senderName;
            if (fromMe != null && fromMe) {
                if (finalSenderName == null || finalSenderName.trim().isEmpty()) {
                    finalSenderName = chat.getName();
                    log.info("🔧 SenderName vazio para áudio enviado, usando nome do chat: {}", finalSenderName);
                }
            }

            // ===== SALVAR ÁUDIO =====
            audioService.saveIncomingAudio(
                    chat.getId(), messageId, instanceId, connectedPhone, phone, fromMe,
                    momment, seconds, audioUrl, mimeType, viewOnce, isStatusReply,
                    finalSenderName, senderPhoto, status
            );

            // ===== ATUALIZAR lastMessageTime =====
            chat.setLastMessageTime(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(momment),
                    java.time.ZoneId.systemDefault()
            ));
            chat = chatRepository.save(chat);

            log.info("✅ Áudio processado com sucesso - MessageId: {}, Chat: {}, SenderName: {}",
                    messageId, chat.getId(), finalSenderName);

            // ✅ NOVO: VERIFICAR SE DEVE REMOVER DA REPESCAGEM
            checkAndRemoveFromRepescagem(chat, fromMe, instance);

            // ===== ENVIAR NOTIFICAÇÃO SSE =====
            if (!fromMe) {
                sendNotificationToUser(instance.getUser().getId(), chat, chat.getLastMessageContent(), isNewChat);
            } else {
                sendChatUpdateToUser(instance.getUser().getId(), chat);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao processar áudio", e);
        }
    }

    /**
     * ✅ NOVO: Processar vídeo recebido via webhook
     */
    @Transactional
    public void processVideo(Map<String, Object> payload, Map<String, Object> videoObj,
                             Boolean fromMe, Long momment, String connectedPhone, String phone,
                             String instanceId, String messageId, String chatName, String senderName,
                             String status, String senderPhoto, Boolean isForwarded, Boolean isGroup) {
        try {
            log.info("🎥 Processando vídeo do webhook");

            // ===== EXTRAIR DADOS DO VÍDEO =====
            String videoUrl = (String) videoObj.get("videoUrl");
            String caption = (String) videoObj.get("caption");
            Integer width = videoObj.get("width") != null ? ((Number) videoObj.get("width")).intValue() : 0;
            Integer height = videoObj.get("height") != null ? ((Number) videoObj.get("height")).intValue() : 0;
            Integer seconds = videoObj.get("seconds") != null ? ((Number) videoObj.get("seconds")).intValue() : 0;
            String mimeType = (String) videoObj.get("mimeType");
            Boolean viewOnce = (Boolean) videoObj.get("viewOnce");
            Boolean isGif = (Boolean) videoObj.get("isGif");
            Boolean isStatusReply = (Boolean) payload.get("isStatusReply");
            Boolean isEdit = (Boolean) payload.get("isEdit");
            Boolean isNewsletter = (Boolean) payload.get("isNewsletter");

            log.info("🎥 Dados do vídeo - VideoUrl: {}, Width: {}, Height: {}, Seconds: {}, Caption: {}",
                    videoUrl != null ? "presente" : "null", width, height, seconds, caption != null ? "presente" : "null");

            // ===== BUSCAR INSTÂNCIA =====
            Optional<WebInstance> instanceOpt = Optional.empty();

            if (instanceId != null && !instanceId.trim().isEmpty()) {
                instanceOpt = webInstanceRepository.findBySuaInstancia(instanceId);
            }

            if (instanceOpt.isEmpty() && connectedPhone != null) {
                instanceOpt = webInstanceRepository.findByConnectedPhone(connectedPhone);
            }

            if (instanceOpt.isEmpty()) {
                log.warn("⚠️ WebInstance não encontrada para vídeo");
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
                chat.setLastMessageContent("Vídeo 🎥");
                chat = chatRepository.save(chat);
                isNewChat = true;
                log.info("✅ Novo chat criado para vídeo - ID: {}", chat.getId());
            } else {
                chat = chatOpt.get();
                int previousUnread = chat.getUnread();

                if (chatName != null && !chatName.equals(chat.getName())) {
                    chat.setName(chatName);
                }

                if (senderPhoto != null && !senderPhoto.isEmpty()) {
                    chat.setProfileThumbnail(senderPhoto);
                }

                chat.setLastMessageContent("Vídeo 🎥");

                if (fromMe) {
                    chat.setUnread(0);
                    log.info("📤 Vídeo enviado - Resetando contador (unread: {} → 0)", previousUnread);
                } else {
                    // ✅ CORREÇÃO: Recarregar chat do banco para evitar race condition
                    chat = chatRepository.findById(chat.getId()).orElse(chat);
                    chat.setUnread(chat.getUnread() + 1);
                    log.info("📥 Vídeo recebido - Incrementando contador (unread: {} → {})",
                            previousUnread, chat.getUnread());
                }

                chat = chatRepository.save(chat);
            }

            // ===== SALVAR VÍDEO =====
            videoService.saveIncomingVideo(
                    chat.getId(), messageId, instanceId, phone, fromMe, momment,
                    videoUrl, caption, width, height, seconds, mimeType, viewOnce, isGif,
                    isStatusReply, isEdit, isGroup, isNewsletter, isForwarded,
                    chatName, senderName, status
            );

            // ✅ NOVO: VERIFICAR SE É UPLOAD E AUTO-SALVAR NA GALERIA
            User owner = instance.getUser();
            if (owner.getUploadPhoneNumber() != null &&
                    owner.getUploadPhoneNumber().equals(phone) &&
                    !fromMe) {

                log.info("🎥 Vídeo recebido no número de upload - Auto-salvando na galeria");

                // Marcar chat como upload chat
                if (!chat.getIsUploadChat()) {
                    chat.setIsUploadChat(true);
                    chatRepository.save(chat);
                }

                // Buscar o vídeo recém-salvo e marcar como salvo na galeria
                videoRepository.findByMessageId(messageId).ifPresent(video -> {
                    video.setSavedInGallery(true);
                    videoRepository.save(video);
                    log.info("✅ Vídeo auto-salvo na galeria - VideoId: {}", video.getId());
                });
            }

            // ===== ATUALIZAR lastMessageTime =====
            chat.setLastMessageTime(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(momment),
                    java.time.ZoneId.systemDefault()
            ));
            chat = chatRepository.save(chat);

            log.info("✅ Vídeo processado com sucesso - MessageId: {}, Chat: {}", messageId, chat.getId());

            // ✅ NOVO: VERIFICAR SE DEVE REMOVER DA REPESCAGEM
            checkAndRemoveFromRepescagem(chat, fromMe, instance);

            // ===== ENVIAR NOTIFICAÇÃO SSE =====
            if (!fromMe) {
                sendNotificationToUser(instance.getUser().getId(), chat, chat.getLastMessageContent(), isNewChat);
            } else {
                sendChatUpdateToUser(instance.getUser().getId(), chat);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao processar vídeo", e);
        }
    }

    /**
     * ✅ NOVO: Verifica se o chat está em repescagem e remove automaticamente quando cliente responde
     *
     * Este método é chamado após salvar qualquer tipo de mensagem (texto, áudio, foto, vídeo)
     * para verificar se o chat deve sair automaticamente da repescagem.
     *
     * @param chat O chat que recebeu a mensagem
     * @param fromMe Indica se a mensagem foi enviada pelo sistema (true) ou pelo cliente (false)
     * @param instance A instância do WhatsApp associada
     */
    private void checkAndRemoveFromRepescagem(Chat chat, Boolean fromMe, WebInstance instance) {
        try {
            // Apenas remove da repescagem se a mensagem foi RECEBIDA do cliente (fromMe=false)
            if (fromMe != null && !fromMe) {
                // Verifica se o chat está na coluna de repescagem
                if (REPESCAGEM_COLUMN.equals(chat.getColumn())) {
                    log.info("🔔 [CHAT: {}] Mensagem do cliente detectada durante repescagem, removendo automaticamente...",
                            chat.getId());

                    // Busca o estado de rotina do chat
                    Optional<ChatRoutineState> stateOpt = chatRoutineStateRepository.findByChatId(chat.getId());

                    if (stateOpt.isPresent()) {
                        ChatRoutineState state = stateOpt.get();

                        // Chama o método do RoutineAutomationService para remover da repescagem
                        // Este método já cuida de:
                        // 1. Mover o chat de volta para previousColumn
                        // 2. Marcar inRepescagem como false
                        // 3. Enviar notificação SSE para atualizar o frontend
                        routineAutomationService.removeFromRepescagem(chat, state, instance.getUser());

                        log.info("✅ [CHAT: {}] Chat removido da repescagem com sucesso!", chat.getId());
                    } else {
                        log.warn("⚠️ [CHAT: {}] Chat em repescagem mas sem ChatRoutineState encontrado",
                                chat.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao verificar e remover da repescagem", chat.getId(), e);
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
            notificationData.put("lastMessageContent", chat.getLastMessageContent());
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
            chatData.put("lastMessageContent", chat.getLastMessageContent());
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
     * ✅ NOVO: Processar documento recebido via webhook
     */
    @Transactional
    public void processDocument(Map<String, Object> payload, Map<String, Object> documentObj,
                                Boolean fromMe, Long momment, String connectedPhone, String phone,
                                String instanceId, String messageId, String chatName, String senderName,
                                String status, String senderPhoto, Boolean isForwarded, Boolean isGroup) {
        try {
            log.info("📄 Processando documento do webhook");

            // ===== EXTRAIR DADOS DO DOCUMENTO =====
            String documentUrl = (String) documentObj.get("documentUrl");
            String caption = (String) documentObj.get("caption");
            String fileName = (String) documentObj.get("fileName");
            String mimeType = (String) documentObj.get("mimeType");
            String title = (String) documentObj.get("title");
            Integer pageCount = documentObj.get("pageCount") != null ?
                    ((Number) documentObj.get("pageCount")).intValue() : null;

            Boolean isStatusReply = (Boolean) payload.get("isStatusReply");
            Boolean isEdit = (Boolean) payload.get("isEdit");
            Boolean isNewsletter = (Boolean) payload.get("isNewsletter");

            log.info("📄 Dados do documento - DocumentUrl: {}, FileName: {}, Caption: {}",
                    documentUrl != null ? "presente" : "null", fileName, caption != null ? "presente" : "null");

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
                log.info("📄 Criando novo chat para documento - Phone: {}", phone);
                chat = new Chat();
                chat.setWebInstance(instance);
                chat.setPhone(phone);
                chat.setName(chatName != null ? chatName : phone);
                chat.setIsGroup(isGroup != null ? isGroup : false);
                chat.setUnread(fromMe ? 0 : 1);
                chat.setColumn("inbox");
                chat.setLastMessageTime(LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(momment),
                        java.time.ZoneId.systemDefault()
                ));

                // Definir caption ou mensagem padrão
                if (caption != null && !caption.isEmpty()) {
                    chat.setLastMessageContent(caption);
                } else {
                    chat.setLastMessageContent("📄 " + (fileName != null ? fileName : "Documento"));
                }

                chat = chatRepository.save(chat);
                isNewChat = true;
            } else {
                chat = chatOpt.get();
                log.info("📄 Chat encontrado - ChatId: {}", chat.getId());

                if (!fromMe) {
                    chat.setUnread(chat.getUnread() + 1);
                }
            }

            // ✅ CORREÇÃO: Verificar se documento já existe antes de salvar (evita duplicação)
            Optional<com.example.loginauthapi.entities.Document> existingDoc =
                    documentRepository.findByMessageId(messageId);

            if (existingDoc.isPresent()) {
                // Documento já existe, apenas atualizar dados se necessário
                com.example.loginauthapi.entities.Document doc = existingDoc.get();
                log.info("ℹ️ Documento já existe, atualizando - MessageId: {}", messageId);

                // Atualizar com dados do webhook (documentUrl correto)
                doc.setDocumentUrl(documentUrl);
                doc.setStatus(status);
                doc.setFileName(fileName);
                doc.setMimeType(mimeType);
                doc.setPageCount(pageCount);
                doc.setTitle(title);
                doc.setCaption(caption);

                documentRepository.save(doc);
                log.info("✅ Documento atualizado - MessageId: {}", messageId);
            } else {
                // Documento não existe, criar novo
                log.info("💾 Salvando novo documento - MessageId: {}", messageId);

                // ===== SALVAR DOCUMENTO =====
                documentService.saveIncomingDocument(
                        chat.getId(), messageId, instanceId, phone, fromMe, momment,
                        documentUrl, fileName, mimeType, pageCount, title, caption,
                        isStatusReply, isEdit, isGroup, isNewsletter, isForwarded,
                        chatName, senderName, status
                );
            }

            // ===== ATUALIZAR lastMessageTime =====
            chat.setLastMessageTime(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(momment),
                    java.time.ZoneId.systemDefault()
            ));

            // Atualizar lastMessageContent
            if (caption != null && !caption.isEmpty()) {
                chat.setLastMessageContent(caption);
            } else {
                chat.setLastMessageContent("📄 " + (fileName != null ? fileName : "Documento"));
            }

            chat = chatRepository.save(chat);

            log.info("✅ Documento processado com sucesso - MessageId: {}, Chat: {}", messageId, chat.getId());

            // ✅ VERIFICAR SE DEVE REMOVER DA REPESCAGEM
            checkAndRemoveFromRepescagem(chat, fromMe, instance);

            // ===== ENVIAR NOTIFICAÇÃO SSE =====
            if (!fromMe) {
                sendNotificationToUser(instance.getUser().getId(), chat, chat.getLastMessageContent(), isNewChat);
            } else {
                sendChatUpdateToUser(instance.getUser().getId(), chat);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao processar documento", e);
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