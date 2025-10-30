package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.PhotoDTO;
import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.Photo;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.PhotoRepository;
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

        Photo saved = photoRepository.save(photo);
        log.info("✅ Foto criada - MessageId: {}, ChatId: {}, ImageUrl: {}, Caption: {}",
                messageId, chatId, saved.getImageUrl(), caption);

        return saved;
    }

    /**
     * Buscar fotos de um chat
     */
    public List<PhotoDTO> getPhotosByChatId(String chatId) {
        if (!chatRepository.existsById(chatId)) {
            throw new RuntimeException("Chat não encontrado");
        }

        List<Photo> photos = photoRepository.findByChatIdOrderByTimestampAsc(chatId);

        return photos.stream()
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
     * Marcar/desmarcar foto como salva na galeria
     */
    @Transactional
    public PhotoDTO togglePhotoInGallery(String photoId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Foto não encontrada"));

        photo.setSavedInGallery(!photo.getSavedInGallery());
        Photo updated = photoRepository.save(photo);

        log.info("✅ Foto {} {} na galeria",
                photo.getSavedInGallery() ? "salva" : "removida",
                photo.getSavedInGallery() ? "para" : "da");

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
     * ✅ MODIFICADO: Converter para DTO (adicionado caption)
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
                .caption(photo.getCaption())  // ✅ NOVO: Incluir caption
                .isStatusReply(photo.getIsStatusReply())
                .isEdit(photo.getIsEdit())
                .isGroup(photo.getIsGroup())
                .isNewsletter(photo.getIsNewsletter())
                .forwarded(photo.getForwarded())
                .chatName(photo.getChatName())
                .senderName(photo.getSenderName())
                .status(photo.getStatus())
                .savedInGallery(photo.getSavedInGallery())
                .build();
    }
}