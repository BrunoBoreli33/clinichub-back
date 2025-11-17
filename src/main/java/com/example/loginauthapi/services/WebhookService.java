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
import com.example.loginauthapi.repositories.MessageRepository;
import com.example.loginauthapi.repositories.AudioRepository;
import com.example.loginauthapi.entities.Message;
import com.example.loginauthapi.entities.Audio;
import com.example.loginauthapi.entities.Photo;
import com.example.loginauthapi.entities.Video;
import com.example.loginauthapi.entities.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

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

    // ✅ NOVO: Injeção para Reply Service
    private final ReplyService replyService;

    // ✅ NOVO: Injeções para processamento de reply
    private final MessageRepository messageRepository;
    private final AudioRepository audioRepository;

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

            // ✅ NOVO: Extrair chatLid do payload (identificador LID do WhatsApp)
            String chatLid = (String) payload.get("chatLid");

            // ✅ NOVO: Extrair referenceMessageId para detectar replies
            String referenceMessageId = (String) payload.get("referenceMessageId");

            boolean isReply = referenceMessageId != null && !referenceMessageId.trim().isEmpty();

            // ✅ CORREÇÃO: Se tem referenceMessageId MAS NÃO tem mídia, processar como reply de texto
            if (isReply) {
                log.info("🔄 Webhook de REPLY detectado - ReferenceMessageId: {}", referenceMessageId);

                // Verificar se tem mídia anexada
                boolean hasMidia = payload.get("image") != null ||
                        payload.get("audio") != null ||
                        payload.get("video") != null ||
                        payload.get("document") != null;

                // Se NÃO tem mídia, processar como reply de texto puro
                if (!hasMidia) {
                    processReplyMessage(payload, referenceMessageId, messageId, fromMe, momment,
                            connectedPhone, instanceId, status);
                    return;
                }
                // Se TEM mídia, continuar o fluxo normal para processar a mídia primeiro
            }

            // ✅ NOVO: Verificar se é imagem
            @SuppressWarnings("unchecked")
            Map<String, Object> imageObj = (Map<String, Object>) payload.get("image");

            if (imageObj != null) {
                // ✅ PROCESSAR FOTO
                processPhoto(payload, imageObj, fromMe, momment, connectedPhone, phone,
                        instanceId, messageId, chatName, senderName, status,
                        senderPhoto, isForwarded, isGroup);

                // ✅ Se for reply de imagem, salvar o reply após processar a foto
                if (isReply) {
                    saveReplyForMedia(payload, referenceMessageId, messageId, fromMe, momment, phone, instanceId);
                }
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

                // ✅ Se for reply de áudio, salvar o reply após processar o áudio
                if (isReply) {
                    saveReplyForMedia(payload, referenceMessageId, messageId, fromMe, momment, phone, instanceId);
                }
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

                // ✅ Se for reply de vídeo, salvar o reply após processar o vídeo
                if (isReply) {
                    saveReplyForMedia(payload, referenceMessageId, messageId, fromMe, momment, phone, instanceId);
                }
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

                // ✅ Se for reply de documento, salvar o reply após processar o documento
                if (isReply) {
                    saveReplyForMedia(payload, referenceMessageId, messageId, fromMe, momment, phone, instanceId);
                }
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

            // ===== ✅ NOVA LÓGICA: BUSCAR OU CRIAR CHAT COM SUPORTE A LID =====
            Chat chat = findOrCreateChatWithLidSupport(
                    instance, phone, chatLid, chatName, senderPhoto, isGroup, fromMe, content
            );
            boolean isNewChat = false; // Mantido para compatibilidade, mas não mais usado

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

            log.info("✅ Mensagem de texto salva no banco - MessageId: {}", messageId);

            // ✅ NOVO: Se tiver referenceMessageId, é um reply
            if (referenceMessageId != null && !referenceMessageId.isEmpty()) {
                log.info("💬 Detectado reply - MessageId: {}, ReferenceId: {}",
                        messageId, referenceMessageId);

                // Determinar o tipo de reply baseado no conteúdo referenciado
                String replyType = determineReplyType(referenceMessageId);

                // Salvar o reply baseado no tipo
                switch (replyType) {
                    case "image":
                        replyService.saveImageReply(messageId, referenceMessageId, chat.getId(),
                                content, fromMe, momment);
                        break;
                    case "audio":
                        replyService.saveAudioReply(messageId, referenceMessageId, chat.getId(),
                                content, fromMe, momment);
                        break;
                    case "video":
                        replyService.saveVideoReply(messageId, referenceMessageId, chat.getId(),
                                content, fromMe, momment);
                        break;
                    case "document":
                        replyService.saveDocumentReply(messageId, referenceMessageId, chat.getId(),
                                content, fromMe, momment);
                        break;
                    default:
                        replyService.saveTextReply(messageId, referenceMessageId, chat.getId(),
                                content, fromMe, momment);
                        break;
                }
            }

            // ✅ ATUALIZAR lastMessageTime do chat
            chat.setLastMessageTime(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(momment),
                    java.time.ZoneId.systemDefault()
            ));

            // ===== ATUALIZAR CHAT COM NOVA MENSAGEM (DEPOIS DE ATUALIZAR TIMESTAMP) =====
            chat = updateChatWithNewMessage(chat, chatName, senderPhoto, content, fromMe);

            // ✅ NOVO: VERIFICAR SE DEVE REMOVER DA REPESCAGEM
            checkAndRemoveFromRepescagem(chat, fromMe, instance);

            // ✅ FORÇAR NOTIFICAÇÃO SSE SEMPRE (GARANTIA DE ENTREGA)
            log.info("🔔 Forçando envio de notificação SSE - FromMe: {}, UserId: {}, ChatId: {}",
                    fromMe, instance.getUser().getId(), chat.getId());

            if (!fromMe) {
                sendNotificationToUser(instance.getUser().getId(), chat, content, isNewChat);
                log.info("✅ Notificação SSE de nova mensagem enviada");
            } else {
                sendChatUpdateToUser(instance.getUser().getId(), chat);
                log.info("✅ Notificação SSE de chat-update enviada");
            }

        } catch (Exception e) {
            log.error("❌ Erro ao processar mensagem", e);
            throw e; // ✅ Re-lançar exceção para que controller capture
        }
    }


    /**
     * ✅ NOVO: Salvar reply após processar mídia (foto, áudio, vídeo, documento)
     * Este método é chamado quando um reply contém mídia anexada
     */
    private void saveReplyForMedia(Map<String, Object> payload, String referenceMessageId,
                                   String messageId, Boolean fromMe, Long momment,
                                   String phone, String instanceId) {
        try {
            log.info("📎 Salvando reply de mídia - MessageId: {}, ReferenceId: {}",
                    messageId, referenceMessageId);

            // Buscar instância e chat
            Optional<WebInstance> instanceOpt = webInstanceRepository.findBySuaInstancia(instanceId);
            if (instanceOpt.isEmpty()) {
                log.warn("⚠️ WebInstance não encontrada ao salvar reply de mídia");
                return;
            }

            Optional<Chat> chatOpt = chatRepository.findByWebInstanceIdAndPhone(
                    instanceOpt.get().getId(), phone);
            if (chatOpt.isEmpty()) {
                log.warn("⚠️ Chat não encontrado ao salvar reply de mídia - Phone: {}", phone);
                return;
            }

            Chat chat = chatOpt.get();

            // Extrair conteúdo da mensagem (caption ou descrição da mídia)
            String replyContent = extractMessageContent(payload);

            // Determinar tipo da mensagem original e salvar reply apropriado
            String replyType = determineReplyType(referenceMessageId);

            switch (replyType) {
                case "image":
                    replyService.saveImageReply(messageId, referenceMessageId, chat.getId(),
                            replyContent, fromMe, momment);
                    log.info("✅ Reply de mídia salvo (tipo: image)");
                    break;
                case "audio":
                    replyService.saveAudioReply(messageId, referenceMessageId, chat.getId(),
                            replyContent, fromMe, momment);
                    log.info("✅ Reply de mídia salvo (tipo: audio)");
                    break;
                case "video":
                    replyService.saveVideoReply(messageId, referenceMessageId, chat.getId(),
                            replyContent, fromMe, momment);
                    log.info("✅ Reply de mídia salvo (tipo: video)");
                    break;
                case "document":
                    replyService.saveDocumentReply(messageId, referenceMessageId, chat.getId(),
                            replyContent, fromMe, momment);
                    log.info("✅ Reply de mídia salvo (tipo: document)");
                    break;
                default:
                    replyService.saveTextReply(messageId, referenceMessageId, chat.getId(),
                            replyContent, fromMe, momment);
                    log.info("✅ Reply de mídia salvo (tipo: text)");
                    break;
            }

        } catch (Exception e) {
            log.error("❌ Erro ao salvar reply de mídia", e);
            // Não lançar exceção - a mídia já foi processada com sucesso
        }
    }

    /**
     * ✅ NOVO: Determinar tipo de reply baseado na mensagem referenciada
     */
    private String determineReplyType(String referenceMessageId) {
        // Verificar se é uma foto
        if (photoRepository.findByMessageId(referenceMessageId).isPresent()) {
            return "image";
        }
        // Verificar se é um áudio
        if (audioService.findByMessageId(referenceMessageId).isPresent()) {
            return "audio";
        }
        // Verificar se é um vídeo
        if (videoRepository.findByMessageId(referenceMessageId).isPresent()) {
            return "video";
        }
        // Verificar se é um documento
        if (documentRepository.findByMessageId(referenceMessageId).isPresent()) {
            return "document";
        }
        // Por padrão, é texto
        return "text";
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


            // ✅ ADICIONAR: Extrair chatLid do payload
            String chatLid = (String) payload.get("chatLid");
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
            // ===== BUSCAR OU CRIAR CHAT COM SUPORTE LID =====
            String photoContent = caption != null && !caption.isEmpty() ? caption : "Foto 📸";
            Chat chat = findOrCreateChatWithLidSupport(
                    instance, phone, chatLid, chatName, senderPhoto, isGroup, fromMe, photoContent
            );
            boolean isNewChat = false;

            // ===== ATUALIZAR CHAT =====
            chat = updateChatWithNewMessage(chat, chatName, senderPhoto, photoContent, fromMe);

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

            // ===== FORÇAR NOTIFICAÇÃO SSE PARA FOTO =====
            log.info("🔔 Forçando notificação SSE para foto - FromMe: {}, ChatId: {}",
                    fromMe, chat.getId());

            if (!fromMe) {
                sendNotificationToUser(instance.getUser().getId(), chat, chat.getLastMessageContent(), isNewChat);
                log.info("✅ Notificação SSE de foto enviada");
            } else {
                sendChatUpdateToUser(instance.getUser().getId(), chat);
                log.info("✅ Chat-update de foto enviado");
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


            // ✅ ADICIONAR: Extrair chatLid do payload
            String chatLid = (String) payload.get("chatLid");
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


            // ===== BUSCAR OU CRIAR CHAT COM SUPORTE LID =====
            String audioContent = "🎤 Áudio " + seconds + "s";
            Chat chat = findOrCreateChatWithLidSupport(
                    instance, phone, chatLid, chatName, senderPhoto, isGroup, fromMe, audioContent
            );
            boolean isNewChat = false;

            // ===== ATUALIZAR CHAT =====
            chat = updateChatWithNewMessage(chat, chatName, senderPhoto, audioContent, fromMe);

            // ✅ NOVO: Garantir que senderName esteja correto
            String finalSenderName = senderName;
            if (fromMe != null && fromMe) {
                if (finalSenderName == null || finalSenderName.trim().isEmpty()) {
                    finalSenderName = chat.getName();
                    log.info("🔧 SenderName vazio para áudio enviado, usando nome do chat: {}", finalSenderName);
                }
            }

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

            // ===== FORÇAR NOTIFICAÇÃO SSE PARA ÁUDIO =====
            log.info("🔔 Forçando notificação SSE para áudio - FromMe: {}, ChatId: {}",
                    fromMe, chat.getId());

            if (!fromMe) {
                sendNotificationToUser(instance.getUser().getId(), chat, chat.getLastMessageContent(), isNewChat);
                log.info("✅ Notificação SSE de áudio enviada");
            } else {
                sendChatUpdateToUser(instance.getUser().getId(), chat);
                log.info("✅ Chat-update de áudio enviado");
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

            // ✅ ADICIONAR: Extrair chatLid do payload
            String chatLid = (String) payload.get("chatLid");
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
            // ===== BUSCAR OU CRIAR CHAT COM SUPORTE LID =====
            String videoContent = caption != null && !caption.isEmpty() ? caption : "Vídeo 🎥";
            Chat chat = findOrCreateChatWithLidSupport(
                    instance, phone, chatLid, chatName, senderPhoto, isGroup, fromMe, videoContent
            );
            boolean isNewChat = false;

            // ===== ATUALIZAR CHAT =====
            chat = updateChatWithNewMessage(chat, chatName, senderPhoto, videoContent, fromMe);

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

            // ===== FORÇAR NOTIFICAÇÃO SSE PARA VÍDEO =====
            log.info("🔔 Forçando notificação SSE para vídeo - FromMe: {}, ChatId: {}",
                    fromMe, chat.getId());

            if (!fromMe) {
                sendNotificationToUser(instance.getUser().getId(), chat, chat.getLastMessageContent(), isNewChat);
                log.info("✅ Notificação SSE de vídeo enviada");
            } else {
                sendChatUpdateToUser(instance.getUser().getId(), chat);
                log.info("✅ Chat-update de vídeo enviado");
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


            // ✅ ADICIONAR: Extrair chatLid do payload
            String chatLid = (String) payload.get("chatLid");
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
            // ===== BUSCAR OU CRIAR CHAT COM SUPORTE LID =====
            String documentContent;
            if (caption != null && !caption.isEmpty()) {
                documentContent = caption;
            } else {
                documentContent = "📄 " + (fileName != null ? fileName : "Documento");
            }

            Chat chat = findOrCreateChatWithLidSupport(
                    instance, phone, chatLid, chatName, senderPhoto, isGroup, fromMe, documentContent
            );
            boolean isNewChat = false;

            // ===== ATUALIZAR CHAT =====
            chat = updateChatWithNewMessage(chat, chatName, senderPhoto, documentContent, fromMe);

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

            // ===== FORÇAR NOTIFICAÇÃO SSE PARA DOCUMENTO =====
            log.info("🔔 Forçando notificação SSE para documento - FromMe: {}, ChatId: {}",
                    fromMe, chat.getId());

            if (!fromMe) {
                sendNotificationToUser(instance.getUser().getId(), chat, chat.getLastMessageContent(), isNewChat);
                log.info("✅ Notificação SSE de documento enviada");
            } else {
                sendChatUpdateToUser(instance.getUser().getId(), chat);
                log.info("✅ Chat-update de documento enviado");
            }

        } catch (Exception e) {
            log.error("❌ Erro ao processar documento", e);
        }
    }

    /**
     * ✅ MODIFICADO: Processar mensagem de REPLY
     * SEMPRE salva na tabela replies, mesmo quando mensagem original não existe
     */
    @Transactional
    public void processReplyMessage(Map<String, Object> payload, String referenceMessageId,
                                    String messageId, Boolean fromMe, Long momment,
                                    String connectedPhone, String instanceId, String status) {
        try {
            log.info("🔍 Processando REPLY - ReferenceMessageId: {}", referenceMessageId);

            // 1️⃣ BUSCAR A MENSAGEM ORIGINAL PELO referenceMessageId
            Optional<Message> originalMessageOpt = messageRepository.findByMessageId(referenceMessageId);

            Chat chat;
            String correctPhone;
            String senderNameFromPayload = (String) payload.get("senderName");
            String chatNameFromPayload = (String) payload.get("chatName");

            if (originalMessageOpt.isEmpty()) {
                log.warn("⚠️ Mensagem original não encontrada para referenceMessageId: {}", referenceMessageId);
                log.info("📌 Usando phone do webhook como fallback");

                // FALLBACK: Usar phone do webhook para encontrar o chat
                String webhookPhone = (String) payload.get("phone");

                if (webhookPhone == null || webhookPhone.trim().isEmpty()) {
                    log.error("❌ Phone do webhook também está vazio, impossível processar reply");
                    return;
                }

                // Buscar instância
                Optional<WebInstance> instanceOpt = Optional.empty();
                if (instanceId != null && !instanceId.trim().isEmpty()) {
                    instanceOpt = webInstanceRepository.findBySuaInstancia(instanceId);
                }
                if (instanceOpt.isEmpty() && connectedPhone != null) {
                    instanceOpt = webInstanceRepository.findByConnectedPhone(connectedPhone);
                }

                if (instanceOpt.isEmpty()) {
                    log.error("❌ WebInstance não encontrada");
                    return;
                }

                WebInstance instance = instanceOpt.get();

                // Buscar chat pelo phone do webhook
                Optional<Chat> chatOpt = chatRepository.findByWebInstanceIdAndPhone(instance.getId(), webhookPhone);

                if (chatOpt.isEmpty()) {
                    log.warn("⚠️ Chat não encontrado para phone: {}, criando novo chat", webhookPhone);
                    // Processar como mensagem normal, não como reply
                    processMessage(payload);
                    return;
                }

                chat = chatOpt.get();
                correctPhone = webhookPhone;

                log.info("✅ Chat encontrado via fallback - ChatId: {}, Phone: {}", chat.getId(), correctPhone);

            } else {
                Message originalMessage = originalMessageOpt.get();
                chat = originalMessage.getChat();
                correctPhone = chat.getPhone();

                log.info("✅ Mensagem original encontrada - ChatId: {}, Phone: {}", chat.getId(), correctPhone);
            }

            // 2️⃣ BUSCAR INSTÂNCIA
            Optional<WebInstance> instanceOpt = Optional.empty();

            if (instanceId != null && !instanceId.trim().isEmpty()) {
                instanceOpt = webInstanceRepository.findBySuaInstancia(instanceId);
            }

            if (instanceOpt.isEmpty() && connectedPhone != null) {
                instanceOpt = webInstanceRepository.findByConnectedPhone(connectedPhone);
            }

            if (instanceOpt.isEmpty()) {
                log.warn("⚠️ WebInstance não encontrada");
                return;
            }

            WebInstance instance = instanceOpt.get();

            // 3️⃣ EXTRAIR CONTEÚDO DA MENSAGEM ENVIADA (mensagem_enviada)
            String mensagemEnviada = extractMessageContent(payload);

            if (mensagemEnviada == null || mensagemEnviada.trim().isEmpty()) {
                log.warn("⚠️ Mensagem enviada vazia no reply");
                return;
            }

            log.info("📝 Conteúdo extraído do reply: '{}'", mensagemEnviada);

            // 4️⃣ SALVAR A MENSAGEM DE REPLY NO messages
            try {
                messageService.saveIncomingMessage(
                        chat.getId(), messageId, mensagemEnviada,
                        fromMe, momment, status,
                        senderNameFromPayload != null ? senderNameFromPayload : chat.getName(),
                        null,
                        false, chat.getIsGroup()
                );
                log.info("✅ Mensagem de reply salva na tabela messages - MessageId: {}", messageId);
            } catch (Exception e) {
                log.error("❌ Erro ao salvar mensagem de reply no messages", e);
                throw e;
            }

            // 5️⃣ SEMPRE SALVAR NA TABELA replies
            try {
                if (originalMessageOpt.isPresent()) {
                    // Mensagem original encontrada: salvar com dados completos
                    saveReplyBasedOnOriginalMessage(originalMessageOpt.get(), messageId, referenceMessageId,
                            chat.getId(), mensagemEnviada, fromMe, momment);
                    log.info("✅ Reply salvo na tabela replies com mensagem original - MessageId: {}", messageId);
                } else {
                    // Mensagem original NÃO encontrada: salvar com flag
                    replyService.saveReplyWithoutOriginalMessage(
                            messageId, referenceMessageId, chat.getId(),
                            mensagemEnviada, fromMe, momment,
                            senderNameFromPayload != null ? senderNameFromPayload : chatNameFromPayload
                    );
                    log.info("✅ Reply salvo na tabela replies SEM mensagem original (flag ativado) - MessageId: {}", messageId);
                }
            } catch (Exception e) {
                log.error("❌ Erro ao salvar reply na tabela replies", e);
                // Não lançar exceção aqui, pois a mensagem já foi salva
            }

            // 6️⃣ ATUALIZAR CHAT
            chat.setLastMessageTime(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(momment),
                    java.time.ZoneId.systemDefault()
            ));
            chat.setLastMessageContent(mensagemEnviada);

            if (!fromMe) {
                chat.setUnread(chat.getUnread() + 1);
            } else {
                chat.setUnread(0);
            }

            chatRepository.save(chat);
            log.info("✅ Chat atualizado - ChatId: {}", chat.getId());

            // 7️⃣ NOTIFICAÇÕES - FORÇAR SSE PARA REPLY
            checkAndRemoveFromRepescagem(chat, fromMe, instance);

            log.info("🔔 Forçando notificação SSE para reply - FromMe: {}, ChatId: {}",
                    fromMe, chat.getId());

            if (!fromMe) {
                sendNotificationToUser(instance.getUser().getId(), chat, mensagemEnviada, false);
                log.info("✅ Notificação SSE de reply enviada");
            } else {
                sendChatUpdateToUser(instance.getUser().getId(), chat);
                log.info("✅ Chat-update de reply enviado");
            }

            log.info("✅ Reply processado com sucesso - MessageId: {}", messageId);

        } catch (Exception e) {
            log.error("❌ Erro ao processar reply", e);
            throw e;
        }
    }

    /**
     * ✅ NOVO: Extrair conteúdo da mensagem enviada no reply
     */
    private String extractMessageContent(Map<String, Object> payload) {
        // Tentar extrair texto
        @SuppressWarnings("unchecked")
        Map<String, Object> textObj = (Map<String, Object>) payload.get("text");
        if (textObj != null) {
            String message = (String) textObj.get("message");
            if (message != null && !message.trim().isEmpty()) {
                return message;
            }
        }

        // Tentar extrair caption de imagem
        @SuppressWarnings("unchecked")
        Map<String, Object> imageObj = (Map<String, Object>) payload.get("image");
        if (imageObj != null) {
            String caption = (String) imageObj.get("caption");
            return caption != null ? caption : "📷 Imagem";
        }

        // Tentar extrair caption de vídeo
        @SuppressWarnings("unchecked")
        Map<String, Object> videoObj = (Map<String, Object>) payload.get("video");
        if (videoObj != null) {
            String caption = (String) videoObj.get("caption");
            return caption != null ? caption : "🎥 Vídeo";
        }

        // Tentar extrair caption de documento
        @SuppressWarnings("unchecked")
        Map<String, Object> documentObj = (Map<String, Object>) payload.get("document");
        if (documentObj != null) {
            String caption = (String) documentObj.get("caption");
            String fileName = (String) documentObj.get("fileName");
            return caption != null ? caption : ("📄 " + (fileName != null ? fileName : "Documento"));
        }

        // Áudio
        @SuppressWarnings("unchecked")
        Map<String, Object> audioObj = (Map<String, Object>) payload.get("audio");
        if (audioObj != null) {
            return "🎤 Áudio";
        }

        return "";
    }

    /**
     * ✅ NOVO: Salvar reply baseado no tipo da mensagem original
     */
    private void saveReplyBasedOnOriginalMessage(Message originalMessage, String messageId,
                                                 String referenceMessageId, String chatId,
                                                 String mensagemEnviada, Boolean fromMe, Long timestamp) {

        // Verificar se é mensagem de texto
        if (originalMessage.getContent() != null && !originalMessage.getContent().trim().isEmpty()) {
            replyService.saveTextReply(messageId, referenceMessageId, chatId,
                    mensagemEnviada, fromMe, timestamp);
            return;
        }

        // Verificar se é áudio
        Optional<Audio> audioOpt = audioRepository.findByMessageId(referenceMessageId);
        if (audioOpt.isPresent()) {
            replyService.saveAudioReply(messageId, referenceMessageId, chatId,
                    mensagemEnviada, fromMe, timestamp);
            return;
        }

        // Verificar se é imagem
        Optional<Photo> photoOpt = photoRepository.findByMessageId(referenceMessageId);
        if (photoOpt.isPresent()) {
            replyService.saveImageReply(messageId, referenceMessageId, chatId,
                    mensagemEnviada, fromMe, timestamp);
            return;
        }

        // Verificar se é vídeo
        Optional<Video> videoOpt = videoRepository.findByMessageId(referenceMessageId);
        if (videoOpt.isPresent()) {
            replyService.saveVideoReply(messageId, referenceMessageId, chatId,
                    mensagemEnviada, fromMe, timestamp);
            return;
        }

        // Verificar se é documento
        Optional<Document> documentOpt = documentRepository.findByMessageId(referenceMessageId);
        if (documentOpt.isPresent()) {
            replyService.saveDocumentReply(messageId, referenceMessageId, chatId,
                    mensagemEnviada, fromMe, timestamp);
            return;
        }

        // Fallback: salvar como texto se não identificar tipo
        log.warn("⚠️ Tipo de mensagem original não identificado, salvando como texto");
        replyService.saveTextReply(messageId, referenceMessageId, chatId,
                mensagemEnviada, fromMe, timestamp);
    }

    /**
     * ✅ NOVO MÉTODO: Buscar ou criar chat com suporte a chatLid
     *
     * Lógica:
     * 1. Se chatLid existe → buscar por chatLid primeiro
     * 2. Se não encontrar → buscar por phone
     * 3. Se não encontrar → criar novo chat
     * 4. Se encontrar e phone mudou de @lid para real → atualizar phone e name
     */
    private Chat findOrCreateChatWithLidSupport(
            WebInstance instance,
            String phone,
            String chatLid,
            String chatName,
            String senderPhoto,
            Boolean isGroup,
            Boolean fromMe,
            String content) {

        Optional<Chat> chatOpt = Optional.empty();
        boolean phoneWasRevealed = false;

        // 🔍 DEBUG: Log de entrada
        log.info("🔍 [DEBUG] findOrCreateChatWithLidSupport INICIADO");
        log.info("🔍 [DEBUG] Parâmetros recebidos:");
        log.info("🔍 [DEBUG]   - chatLid: '{}'", chatLid);
        log.info("🔍 [DEBUG]   - phone: '{}'", phone);
        log.info("🔍 [DEBUG]   - chatName: '{}'", chatName);
        log.info("🔍 [DEBUG]   - fromMe: {}", fromMe);

        // ✅ PASSO 1: Tentar buscar por chatLid (se existir)
        if (chatLid != null && !chatLid.trim().isEmpty()) {
            log.info("🔍 Buscando chat por chatLid: {}", chatLid);
            chatOpt = chatRepository.findByWebInstanceIdAndChatLid(instance.getId(), chatLid);

            if (chatOpt.isPresent()) {
                log.info("✅ Chat encontrado por chatLid");
                Chat chat = chatOpt.get();

                // 🔍 DEBUG: Info do chat encontrado
                log.info("🔍 [DEBUG] Chat encontrado - ID: {}, Phone atual: '{}', Name: '{}'",
                        chat.getId(), chat.getPhone(), chat.getName());

                // ✅ Verificar se o número foi revelado (mudou de @lid para número real)
                boolean hadLidPhone = chat.getPhone() == null || chat.getPhone().contains("@lid");
                boolean hasRealPhone = phone != null && !phone.contains("@lid");

                // 🔍 DEBUG: Verificação de revelação
                log.info("🔍 [DEBUG] hadLidPhone: {}, hasRealPhone: {}", hadLidPhone, hasRealPhone);

                if (hadLidPhone && hasRealPhone) {
                    phoneWasRevealed = true;
                    log.info("🎉 NÚMERO REVELADO! ChatLid: {}, Phone: {} → {}",
                            chatLid, chat.getPhone(), phone);
                    log.info("🔍 [DEBUG] Verificando se já existe chat com phone: '{}'", phone);

                    // ✅ VERIFICAR SE JÁ EXISTE OUTRO CHAT COM ESSE PHONE
                    Optional<Chat> existingChatWithPhone = chatRepository.findByWebInstanceIdAndPhone(
                            instance.getId(), phone);

                    log.info("🔍 [DEBUG] Chat com phone '{}' existe? {}",
                            phone, existingChatWithPhone.isPresent());

                    if (existingChatWithPhone.isPresent() &&
                            !existingChatWithPhone.get().getId().equals(chat.getId())) {
                        // JÁ EXISTE OUTRO CHAT COM ESSE PHONE!
                        Chat realChat = existingChatWithPhone.get();
                        log.warn("⚠️ JÁ EXISTE chat com phone {}, migrando mensagens e deletando chat temporário", phone);
                        log.info("🔍 [DEBUG] Chat temporário: ID={}, Name='{}'", chat.getId(), chat.getName());
                        log.info("🔍 [DEBUG] Chat real: ID={}, Name='{}'", realChat.getId(), realChat.getName());


                        // ✅ MIGRAR TODAS AS MENSAGENS DO CHAT TEMPORÁRIO PARA O CHAT REAL
                        migrateMessagesFromTemporaryToReal(chat, realChat);

                        // Adicionar chatLid ao chat real
                        if (realChat.getChatLid() == null) {
                            realChat.setChatLid(chatLid);
                            log.info("✅ ChatLid {} adicionado ao chat real {}", chatLid, realChat.getId());
                        }

                        // Atualizar nome e foto se necessário
                        if (isValidRevealedName(chatName)) {
                            realChat.setName(chatName);
                        }
                        if (senderPhoto != null) {
                            realChat.setProfileThumbnail(senderPhoto);
                        }

                        chatRepository.save(realChat);

                        // ✅ CORREÇÃO: Deletar em transação separada para evitar rollback
                        String temporaryChatId = chat.getId();
                        deleteTemporaryChatInNewTransaction(temporaryChatId);

                        return realChat;
                    }

                    // Atualizar phone revelado (só se não houver conflito)
                    log.info("🔍 [DEBUG] Atualizando chat temporário com phone revelado");
                    log.info("🔍 [DEBUG] Antes - Phone: '{}', Name: '{}'", chat.getPhone(), chat.getName());

                    chat.setPhone(phone);

                    // Atualizar name revelado (se não for apenas números)
                    if (isValidRevealedName(chatName)) {
                        chat.setName(chatName);
                        log.info("✅ Nome atualizado: {}", chatName);
                    } else {
                        log.info("🔍 [DEBUG] Nome '{}' NÃO é válido (isValidRevealedName=false)", chatName);
                    }

                    chatRepository.save(chat);
                    log.info("🔍 [DEBUG] Depois - Phone: '{}', Name: '{}'", chat.getPhone(), chat.getName());
                }

                return chat;
            }
        }

        // ✅ PASSO 2: Se não encontrou por chatLid, tentar buscar por phone
        log.info("🔍 [DEBUG] Chat NÃO encontrado por chatLid, tentando por phone");

        // ✅ CORREÇÃO: Buscar por phone MESMO que contenha @lid para evitar duplicação
        if (phone != null && !phone.trim().isEmpty()) {
            log.info("🔍 Buscando chat por phone: {}", phone);
            chatOpt = chatRepository.findByWebInstanceIdAndPhone(instance.getId(), phone);

            if (chatOpt.isPresent()) {
                log.info("✅ Chat encontrado por phone");
                Chat chat = chatOpt.get();

                // Se encontrou por phone mas não tinha chatLid, atualizar
                if (chatLid != null && chat.getChatLid() == null) {
                    chat.setChatLid(chatLid);
                    chatRepository.save(chat);
                    log.info("✅ ChatLid adicionado ao chat existente: {}", chatLid);
                }

                return chat;
            }
        }

        // ✅ PASSO 3: Chat não existe → CRIAR NOVO
        log.info("🆕 Criando novo chat - ChatLid: {}, Phone: {}", chatLid, phone);
        log.info("🔍 [DEBUG] Chat não encontrado nem por chatLid nem por phone, criando NOVO");

        Chat newChat = new Chat();
        newChat.setWebInstance(instance);
        newChat.setChatLid(chatLid); // Sempre salvar chatLid

        // ✅ CORREÇÃO: Salvar phone com @lid para chats temporários
        if (phone != null && phone.contains("@lid")) {
            newChat.setPhone(phone); // ✅ SALVAR PHONE COM @LID para permitir busca no frontend
            log.info("📱 Chat temporário - Phone: {}", phone);
        } else {
            newChat.setPhone(phone); // Número real
            log.info("📱 Phone revelado: {}", phone);
        }

        // Definir name (usar chatName se válido, senão usar phone ou chatLid)
        if (isValidRevealedName(chatName)) {
            newChat.setName(chatName);
        } else if (phone != null && !phone.contains("@lid")) {
            newChat.setName(phone);
        } else {
            // ✅ Para chats temporários, usar o phone completo como nome
            newChat.setName(phone != null ? phone : (chatLid != null ? chatLid : "Desconhecido"));
        }

        newChat.setIsGroup(isGroup != null ? isGroup : false);
        newChat.setUnread(fromMe ? 0 : 1);
        newChat.setColumn("inbox");
        newChat.setProfileThumbnail(senderPhoto);
        newChat.setLastMessageContent(truncateMessage(content, 50));

        newChat = chatRepository.save(newChat);

        log.info("✅ Novo chat criado - ID: {}, ChatLid: {}, Phone: {}, Name: {}",
                newChat.getId(), newChat.getChatLid(), newChat.getPhone(), newChat.getName());

        return newChat;
    }

    /**
     * ✅ NOVO MÉTODO: Deletar chat temporário em nova transação
     * Isso evita que um erro de deleção cause rollback na transação principal
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteTemporaryChatInNewTransaction(String chatId) {
        try {
            Optional<Chat> chatOpt = chatRepository.findById(chatId);
            if (chatOpt.isPresent()) {
                chatRepository.delete(chatOpt.get());
                log.info("🗑️ Chat temporário deletado em nova transação - ID: {}", chatId);
            }
        } catch (Exception e) {
            log.error("❌ Erro ao deletar chat temporário em nova transação - ID: {}", chatId, e);
            // Não re-lançar exceção para não afetar transação principal
        }
    }

    /**
     * ✅ NOVO MÉTODO: Migrar todas as mensagens de um chat temporário para um chat real
     */
    private void migrateMessagesFromTemporaryToReal(Chat temporaryChat, Chat realChat) {
        log.info("🔄 Iniciando migração de mensagens - Temporário: {} → Real: {}",
                temporaryChat.getId(), realChat.getId());

        int totalMigrated = 0;

        // Migrar mensagens de texto
        List<Message> messages = messageRepository.findByChatIdOrderByTimestampAsc(temporaryChat.getId());
        for (Message msg : messages) {
            msg.setChat(realChat);
            totalMigrated++;
        }
        if (!messages.isEmpty()) {
            messageRepository.saveAll(messages);
            log.info("✅ {} mensagens de texto migradas", messages.size());
        }

        // Migrar áudios
        List<Audio> audios = audioRepository.findByChatIdOrderByTimestampAsc(temporaryChat.getId());
        for (Audio audio : audios) {
            audio.setChat(realChat);
            totalMigrated++;
        }
        if (!audios.isEmpty()) {
            audioRepository.saveAll(audios);
            log.info("✅ {} áudios migrados", audios.size());
        }

        // Migrar fotos
        List<Photo> photos = photoRepository.findByChatIdOrderByTimestampAsc(temporaryChat.getId());
        for (Photo photo : photos) {
            photo.setChat(realChat);
            totalMigrated++;
        }
        if (!photos.isEmpty()) {
            photoRepository.saveAll(photos);
            log.info("✅ {} fotos migradas", photos.size());
        }

        // Migrar vídeos
        List<Video> videos = videoRepository.findByChatIdOrderByTimestampAsc(temporaryChat.getId());
        for (Video video : videos) {
            video.setChat(realChat);
            totalMigrated++;
        }
        if (!videos.isEmpty()) {
            videoRepository.saveAll(videos);
            log.info("✅ {} vídeos migrados", videos.size());
        }

        // Migrar documentos
        List<Document> documents = documentRepository.findByChatIdOrderByTimestampAsc(temporaryChat.getId());
        for (Document doc : documents) {
            doc.setChat(realChat);
            totalMigrated++;
        }
        if (!documents.isEmpty()) {
            documentRepository.saveAll(documents);
            log.info("✅ {} documentos migrados", documents.size());
        }

        log.info("✅ Migração concluída - Total de itens migrados: {}", totalMigrated);
    }

    /**
     * ✅ NOVO MÉTODO: Verificar se o chatName é um nome válido revelado
     * Retorna false se for apenas números (indicando que ainda está oculto)
     */
    private boolean isValidRevealedName(String chatName) {
        if (chatName == null || chatName.trim().isEmpty()) {
            return false;
        }

        // Verificar se contém @lid
        if (chatName.contains("@lid")) {
            return false;
        }

        // Verificar se é apenas números (indicando nome oculto)
        if (chatName.matches("\\d+")) {
            return false;
        }

        return true;
    }

    /**
     * ✅ NOVO MÉTODO: Atualizar chat com nova mensagem
     */
    private Chat updateChatWithNewMessage(Chat chat, String chatName, String senderPhoto,
                                          String content, Boolean fromMe) {
        int previousUnread = chat.getUnread();

        // Atualizar nome se mudou (e é válido)
        if (isValidRevealedName(chatName) && !chatName.equals(chat.getName())) {
            chat.setName(chatName);
            log.info("✅ Nome do chat atualizado: {}", chatName);
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
        return chat;
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