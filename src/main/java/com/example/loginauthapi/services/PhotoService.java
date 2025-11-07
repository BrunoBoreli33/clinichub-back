package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.PhotoDTO;
import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.Photo;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.PhotoRepository;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final ChatRepository chatRepository;
    private final WebInstanceRepository webInstanceRepository;

    /**
     * ✅ MODIFICADO: Salvar foto recebida via webhook (adicionado parâmetro caption)
     */
    @Transactional
    public Photo saveIncomingPhoto(String chatId, String messageId, String instanceId,
                                   String phone, Boolean fromMe, Long timestamp,
                                   String imageUrl, Integer width, Integer height,
                                   String mimeType, String caption, Boolean isStatusReply, Boolean isEdit,
                                   Boolean isGroup, Boolean isNewsletter, Boolean forwarded,
                                   String chatName, String senderName, String status) {

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        log.info("📸 saveIncomingPhoto - ChatId: {}, MessageId: {}, FromMe: {}, ImageUrl: {}, Caption: {}",
                chatId, messageId, fromMe, imageUrl != null ? "presente" : "null", caption);

        // Verificar se a foto já existe pelo messageId
        Optional<Photo> existing = photoRepository.findByMessageId(messageId);

        if (existing.isPresent()) {
            Photo photo = existing.get();
            log.info("🔄 Foto encontrada pelo messageId, atualizando com dados do webhook");

            // Atualizar com dados do webhook
            photo.setInstanceId(instanceId);
            photo.setTimestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
            photo.setImageUrl(imageUrl != null && !imageUrl.isEmpty() ? imageUrl : photo.getImageUrl());
            photo.setWidth(width != null ? width : photo.getWidth());
            photo.setHeight(height != null ? height : photo.getHeight());
            photo.setMimeType(mimeType);
            // ✅ NOVO: Atualizar caption se presente
            if (caption != null && !caption.isEmpty()) {
                photo.setCaption(caption);
            }
            photo.setSenderName(senderName);
            photo.setStatus(status != null ? status : photo.getStatus());

            Photo updated = photoRepository.save(photo);
            log.info("✅ Foto atualizada com sucesso!");
            return updated;
        }

        // Se não encontrou por messageId, criar nova foto
        log.info("🆕 Criando nova foto - MessageId: {}, FromMe: {}", messageId, fromMe);

        Photo photo = new Photo();
        photo.setChat(chat);
        photo.setMessageId(messageId);
        photo.setInstanceId(instanceId);
        photo.setPhone(phone);
        photo.setFromMe(fromMe);
        photo.setTimestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
        photo.setImageUrl(imageUrl != null ? imageUrl : "");
        photo.setWidth(width != null ? width : 0);
        photo.setHeight(height != null ? height : 0);
        photo.setMimeType(mimeType);
        // ✅ NOVO: Definir caption
        photo.setCaption(caption);
        photo.setIsStatusReply(isStatusReply != null ? isStatusReply : false);
        photo.setIsEdit(isEdit != null ? isEdit : false);
        photo.setIsGroup(isGroup != null ? isGroup : false);
        photo.setIsNewsletter(isNewsletter != null ? isNewsletter : false);
        photo.setForwarded(forwarded != null ? forwarded : false);
        photo.setChatName(chatName);
        photo.setSenderName(senderName);
        photo.setStatus(status != null ? status : "PENDING");
        photo.setSavedInGallery(false);
        photo.setDeletedFromChat(false); // ✅ NOVO: Inicializar

        Photo saved = photoRepository.save(photo);
        log.info("✅ Foto criada - MessageId: {}, ChatId: {}, ImageUrl: {}, Caption: {}",
                messageId, chatId, saved.getImageUrl(), caption);

        return saved;
    }

    /**
     * ✅ MODIFICADO: Buscar fotos de um chat - Filtrar deletadas
     */
    public List<PhotoDTO> getPhotosByChatId(String chatId) {
        if (!chatRepository.existsById(chatId)) {
            throw new RuntimeException("Chat não encontrado");
        }

        List<Photo> photos = photoRepository.findByChatIdOrderByTimestampAsc(chatId);

        return photos.stream()
                .filter(photo -> photo.getDeletedFromChat() == null || !photo.getDeletedFromChat())
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Buscar fotos salvas na galeria do usuário
     */
    public List<PhotoDTO> getSavedGalleryPhotos(String userId) {
        List<Photo> photos = photoRepository.findByChatWebInstanceUserIdAndSavedInGalleryTrueOrderByTimestampDesc(userId);

        return photos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * ✅ MODIFICADO: Marcar/desmarcar foto como salva na galeria + verificar delete permanente
     */
    @Transactional
    public PhotoDTO togglePhotoInGallery(String photoId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Foto não encontrada"));

        boolean wasInGallery = photo.getSavedInGallery();
        photo.setSavedInGallery(!wasInGallery);
        Photo updated = photoRepository.save(photo);

        log.info("✅ Foto {} {} na galeria",
                photo.getSavedInGallery() ? "salva" : "removida",
                photo.getSavedInGallery() ? "para" : "da");

        // ✅ NOVO: Se removeu da galeria E já estava deletada do chat, deletar permanentemente
        if (!photo.getSavedInGallery() && photo.getDeletedFromChat() != null && photo.getDeletedFromChat()) {
            log.info("🗑️ Foto removida da galeria e já deletada do chat - deletando permanentemente");
            photoRepository.delete(photo);
        }

        return convertToDTO(updated);
    }

    /**
     * ✅ CORRIGIDO: Salvar foto enviada (outgoing) antes de enviar via Z-API
     * Se photoId for fornecido, copia todas as informações da foto original
     */
    @Transactional
    public PhotoDTO saveOutgoingPhoto(String chatId, String phone, String imageUrl, String instanceId, String photoId) {
        try {
            Chat chat = chatRepository.findById(chatId)
                    .orElseThrow(() -> new RuntimeException("Chat não encontrado: " + chatId));

            // Gerar messageId temporário
            String tempMessageId = "temp_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString();

            Photo photo = new Photo();
            photo.setMessageId(tempMessageId);
            photo.setChat(chat);
            photo.setImageUrl(imageUrl);
            photo.setTimestamp(LocalDateTime.now());
            photo.setFromMe(true);
            photo.setStatus("PENDING");
            photo.setSenderName(chat.getName());
            photo.setSavedInGallery(false);
            photo.setPhone(phone);
            photo.setInstanceId(instanceId);
            photo.setDeletedFromChat(false); // ✅ NOVO

            // ✅ Se photoId fornecido, copiar informações da foto original
            if (photoId != null && !photoId.isEmpty()) {
                Optional<Photo> originalPhoto = photoRepository.findById(photoId);
                if (originalPhoto.isPresent()) {
                    Photo original = originalPhoto.get();
                    photo.setWidth(original.getWidth());
                    photo.setHeight(original.getHeight());
                    photo.setMimeType(original.getMimeType());
                    // NÃO copiar caption - conforme especificado
                    photo.setCaption(null);
                    log.info("✅ Copiando informações da foto original - PhotoId: {}, Width: {}, Height: {}",
                            photoId, original.getWidth(), original.getHeight());
                } else {
                    log.warn("⚠️ Foto original não encontrada: {}, usando valores padrão", photoId);
                    photo.setCaption(null);
                    photo.setWidth(0);
                    photo.setHeight(0);
                }
            } else {
                // Valores placeholder se não houver photoId
                photo.setCaption(null);
                photo.setWidth(0);
                photo.setHeight(0);
            }

            photo = photoRepository.save(photo);
            log.info("✅ Foto outgoing salva temporariamente - MessageId: {}, InstanceId: {}", tempMessageId, instanceId);

            return convertToDTO(photo);
        } catch (Exception e) {
            log.error("❌ Erro ao salvar foto outgoing", e);
            throw new RuntimeException("Erro ao salvar foto outgoing: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ NOVO: Atualizar messageId da foto após envio via Z-API
     */
    @Transactional
    public void updatePhotoIdAfterSend(String tempMessageId, String realMessageId, String status) {
        try {
            Photo photo = photoRepository.findByMessageId(tempMessageId)
                    .orElseThrow(() -> new RuntimeException("Foto não encontrada: " + tempMessageId));

            photo.setMessageId(realMessageId);
            photo.setStatus(status);
            photoRepository.save(photo);

            log.info("✅ Foto atualizada com messageId real: {} -> {}", tempMessageId, realMessageId);
        } catch (Exception e) {
            log.error("❌ Erro ao atualizar photo após envio", e);
            throw new RuntimeException("Erro ao atualizar photo: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ NOVO: Salvar foto de upload direto (sem chatId pré-definido)
     * Cria ou encontra o chat baseado no phone
     * Automaticamente salva na galeria se o phone corresponder ao upload_phone_number do usuário
     */
    @Transactional
    public PhotoDTO saveUploadPhoto(String phone, String imageUrl, String instanceId, User user) {
        try {
            // Buscar a WebInstance
            WebInstance webInstance = webInstanceRepository.findById(instanceId)
                    .orElseThrow(() -> new RuntimeException("WebInstance não encontrada: " + instanceId));

            // Buscar ou criar chat com base no phone e webInstanceId
            Chat chat = chatRepository.findByPhoneAndWebInstanceId(phone, instanceId)
                    .orElseGet(() -> {
                        Chat newChat = new Chat();
                        newChat.setPhone(phone);
                        newChat.setWebInstance(webInstance);
                        newChat.setName(phone);
                        newChat.setProfileThumbnail(null);
                        newChat.setUnread(0);
                        newChat.setLastMessageTime(LocalDateTime.now());
                        newChat.setActiveInZapi(true);
                        newChat.setIsGroup(false);
                        return chatRepository.save(newChat);
                    });

            // Gerar messageId temporário
            String tempMessageId = "temp_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString();

            Photo photo = new Photo();
            photo.setMessageId(tempMessageId);
            photo.setChat(chat);
            photo.setImageUrl(imageUrl);
            photo.setTimestamp(LocalDateTime.now());
            photo.setFromMe(true);
            photo.setStatus("PENDING");
            photo.setSenderName(chat.getName());
            photo.setPhone(phone);
            photo.setInstanceId(instanceId);
            photo.setCaption(null);
            photo.setWidth(0);
            photo.setHeight(0);
            photo.setDeletedFromChat(false); // ✅ NOVO

            // ✅ Verificar se deve salvar na galeria automaticamente
            boolean shouldSaveInGallery = user.getUploadPhoneNumber() != null
                    && user.getUploadPhoneNumber().equals(phone);
            photo.setSavedInGallery(shouldSaveInGallery);

            photo = photoRepository.save(photo);
            log.info("✅ Foto de upload salva - MessageId: {}, SavedInGallery: {}",
                    tempMessageId, shouldSaveInGallery);

            return convertToDTO(photo);
        } catch (Exception e) {
            log.error("❌ Erro ao salvar foto de upload", e);
            throw new RuntimeException("Erro ao salvar foto de upload: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ MODIFICADO: Excluir foto com soft delete baseado na galeria
     */
    @Transactional
    public void deletePhoto(String messageId) {
        try {
            log.info("🗑️ Excluindo foto - MessageId: {}", messageId);

            Optional<Photo> photoOpt = photoRepository.findByMessageId(messageId);

            if (photoOpt.isPresent()) {
                Photo photo = photoOpt.get();

                // ✅ LÓGICA DE SOFT DELETE
                if (photo.getSavedInGallery() != null && photo.getSavedInGallery()) {
                    // Se está na galeria, apenas marcar como deletada do chat
                    photo.setDeletedFromChat(true);
                    photoRepository.save(photo);
                    log.info("✅ Foto marcada como deletada do chat, mas mantida na galeria - MessageId: {}", messageId);
                } else {
                    // Se não está na galeria, deletar permanentemente
                    photoRepository.delete(photo);
                    log.info("✅ Foto excluída permanentemente do banco - MessageId: {}", messageId);
                }
            } else {
                log.warn("⚠️ Foto não encontrada no banco - MessageId: {}", messageId);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao excluir foto do banco", e);
            throw new RuntimeException("Erro ao excluir foto: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ MODIFICADO: Converter para DTO (adicionado caption e deletedFromChat)
     */
    private PhotoDTO convertToDTO(Photo photo) {
        return PhotoDTO.builder()
                .id(photo.getId())
                .messageId(photo.getMessageId())
                .instanceId(photo.getInstanceId())
                .phone(photo.getPhone())
                .fromMe(photo.getFromMe())
                .timestamp(photo.getTimestamp().toString())
                .imageUrl(photo.getImageUrl())
                .width(photo.getWidth())
                .height(photo.getHeight())
                .mimeType(photo.getMimeType())
                .caption(photo.getCaption())
                .isStatusReply(photo.getIsStatusReply())
                .isEdit(photo.getIsEdit())
                .isGroup(photo.getIsGroup())
                .isNewsletter(photo.getIsNewsletter())
                .forwarded(photo.getForwarded())
                .chatName(photo.getChatName())
                .senderName(photo.getSenderName())
                .status(photo.getStatus())
                .savedInGallery(photo.getSavedInGallery())
                .deletedFromChat(photo.getDeletedFromChat()) // ✅ NOVO
                .build();
    }
}