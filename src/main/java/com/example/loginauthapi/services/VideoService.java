package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.VideoDTO;
import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.Video;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.VideoRepository;
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
public class VideoService {

    private final VideoRepository videoRepository;
    private final ChatRepository chatRepository;

    /**
     * Salvar vídeo recebido via webhook
     */
    @Transactional
    public Video saveIncomingVideo(String chatId, String messageId, String instanceId,
                                   String phone, Boolean fromMe, Long timestamp,
                                   String videoUrl, String caption, Integer width, Integer height,
                                   Integer seconds, String mimeType, Boolean viewOnce,
                                   Boolean isGif, Boolean isStatusReply, Boolean isEdit,
                                   Boolean isGroup, Boolean isNewsletter, Boolean forwarded,
                                   String chatName, String senderName, String status) {

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        log.info("🎥 saveIncomingVideo - ChatId: {}, MessageId: {}, FromMe: {}, VideoUrl: {}, Seconds: {}, Caption: {}",
                chatId, messageId, fromMe, videoUrl != null ? "presente" : "null", seconds, caption != null ? "presente" : "null");

        // Verificar se o vídeo já existe pelo messageId
        Optional<Video> existing = videoRepository.findByMessageId(messageId);

        if (existing.isPresent()) {
            Video video = existing.get();
            log.info("🔄 Vídeo encontrado pelo messageId, atualizando com dados do webhook");

            // Atualizar com dados do webhook
            video.setInstanceId(instanceId);
            video.setTimestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
            video.setVideoUrl(videoUrl != null && !videoUrl.isEmpty() ? videoUrl : video.getVideoUrl());
            video.setCaption(caption);
            video.setWidth(width != null ? width : video.getWidth());
            video.setHeight(height != null ? height : video.getHeight());
            video.setSeconds(seconds != null ? seconds : video.getSeconds());
            video.setMimeType(mimeType);
            video.setViewOnce(viewOnce != null ? viewOnce : video.getViewOnce());
            video.setIsGif(isGif != null ? isGif : video.getIsGif());
            video.setSenderName(senderName);
            video.setStatus(status != null ? status : video.getStatus());

            Video updated = videoRepository.save(video);
            log.info("✅ Vídeo atualizado com sucesso!");
            return updated;
        }

        // Se não encontrou por messageId, criar novo vídeo
        log.info("🆕 Criando novo vídeo - MessageId: {}, FromMe: {}", messageId, fromMe);

        Video video = new Video();
        video.setChat(chat);
        video.setMessageId(messageId);
        video.setInstanceId(instanceId);
        video.setPhone(phone);
        video.setFromMe(fromMe);
        video.setTimestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
        video.setVideoUrl(videoUrl != null ? videoUrl : "");
        video.setCaption(caption);
        video.setWidth(width != null ? width : 0);
        video.setHeight(height != null ? height : 0);
        video.setSeconds(seconds != null ? seconds : 0);
        video.setMimeType(mimeType);
        video.setViewOnce(viewOnce != null ? viewOnce : false);
        video.setIsGif(isGif != null ? isGif : false);
        video.setIsStatusReply(isStatusReply != null ? isStatusReply : false);
        video.setIsEdit(isEdit != null ? isEdit : false);
        video.setIsGroup(isGroup != null ? isGroup : false);
        video.setIsNewsletter(isNewsletter != null ? isNewsletter : false);
        video.setForwarded(forwarded != null ? forwarded : false);
        video.setChatName(chatName);
        video.setSenderName(senderName);
        video.setStatus(status != null ? status : "PENDING");
        video.setSavedInGallery(false);

        Video saved = videoRepository.save(video);
        log.info("✅ Vídeo criado - MessageId: {}, ChatId: {}, VideoUrl: {}, Seconds: {}, Caption: {}",
                messageId, chatId, saved.getVideoUrl(), seconds, caption != null ? "presente" : "null");

        return saved;
    }

    /**
     * Buscar vídeos de um chat
     */
    public List<VideoDTO> getVideosByChatId(String chatId) {
        if (!chatRepository.existsById(chatId)) {
            throw new RuntimeException("Chat não encontrado");
        }

        List<Video> videos = videoRepository.findByChatIdOrderByTimestampAsc(chatId);

        return videos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Buscar vídeos salvos na galeria do usuário
     */
    public List<VideoDTO> getSavedGalleryVideos(String userId) {
        List<Video> videos = videoRepository.findByChatWebInstanceUserIdAndSavedInGalleryTrueOrderByTimestampDesc(userId);

        return videos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Marcar/desmarcar vídeo como salvo na galeria
     */
    @Transactional
    public VideoDTO toggleVideoInGallery(String videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Vídeo não encontrado"));

        video.setSavedInGallery(!video.getSavedInGallery());
        Video updated = videoRepository.save(video);

        log.info("✅ Vídeo {} {} na galeria",
                video.getSavedInGallery() ? "salvo" : "removido",
                video.getSavedInGallery() ? "para" : "da");

        return convertToDTO(updated);
    }

    /**
     * ✅ CORRIGIDO: Salvar vídeo enviado (outgoing) antes de enviar via Z-API
     * Se videoId for fornecido, copia todas as informações do vídeo original
     */
    @Transactional
    public VideoDTO saveOutgoingVideo(String chatId, String phone, String videoUrl, String instanceId, String videoId) {
        try {
            Chat chat = chatRepository.findById(chatId)
                    .orElseThrow(() -> new RuntimeException("Chat não encontrado: " + chatId));

            // Gerar messageId temporário
            String tempMessageId = "temp_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString();

            Video video = new Video();
            video.setMessageId(tempMessageId);
            video.setChat(chat);
            video.setVideoUrl(videoUrl);
            video.setTimestamp(LocalDateTime.now());
            video.setFromMe(true);
            video.setStatus("PENDING");
            video.setSenderName(chat.getName());
            video.setSavedInGallery(false);
            video.setPhone(phone);
            video.setInstanceId(instanceId);

            // ✅ Se videoId fornecido, copiar informações do vídeo original
            if (videoId != null && !videoId.isEmpty()) {
                Optional<Video> originalVideo = videoRepository.findById(videoId);
                if (originalVideo.isPresent()) {
                    Video original = originalVideo.get();
                    video.setWidth(original.getWidth());
                    video.setHeight(original.getHeight());
                    video.setSeconds(original.getSeconds());
                    video.setMimeType(original.getMimeType());
                    video.setViewOnce(original.getViewOnce());
                    video.setIsGif(original.getIsGif());
                    // NÃO copiar caption - conforme especificado
                    video.setCaption(null);
                    log.info("✅ Copiando informações do vídeo original - VideoId: {}, Width: {}, Height: {}, Seconds: {}",
                            videoId, original.getWidth(), original.getHeight(), original.getSeconds());
                } else {
                    log.warn("⚠️ Vídeo original não encontrado: {}, usando valores padrão", videoId);
                    video.setCaption(null);
                    video.setWidth(0);
                    video.setHeight(0);
                    video.setSeconds(0);
                }
            } else {
                // Valores placeholder se não houver videoId
                video.setCaption(null);
                video.setWidth(0);
                video.setHeight(0);
                video.setSeconds(0);
            }

            video = videoRepository.save(video);
            log.info("✅ Vídeo outgoing salvo temporariamente - MessageId: {}, InstanceId: {}", tempMessageId, instanceId);

            return convertToDTO(video);
        } catch (Exception e) {
            log.error("❌ Erro ao salvar vídeo outgoing", e);
            throw new RuntimeException("Erro ao salvar vídeo outgoing: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ NOVO: Atualizar messageId do vídeo após envio via Z-API
     */
    @Transactional
    public void updateVideoIdAfterSend(String tempMessageId, String realMessageId, String status) {
        try {
            Video video = videoRepository.findByMessageId(tempMessageId)
                    .orElseThrow(() -> new RuntimeException("Vídeo não encontrado: " + tempMessageId));

            video.setMessageId(realMessageId);
            video.setStatus(status);
            videoRepository.save(video);

            log.info("✅ Vídeo atualizado com messageId real: {} -> {}", tempMessageId, realMessageId);
        } catch (Exception e) {
            log.error("❌ Erro ao atualizar video após envio", e);
            throw new RuntimeException("Erro ao atualizar video: " + e.getMessage(), e);
        }
    }

    /**
     * Converter para DTO
     */
    private VideoDTO convertToDTO(Video video) {
        return VideoDTO.builder()
                .id(video.getId())
                .messageId(video.getMessageId())
                .instanceId(video.getInstanceId())
                .phone(video.getPhone())
                .fromMe(video.getFromMe())
                .timestamp(video.getTimestamp().toString())
                .videoUrl(video.getVideoUrl())
                .caption(video.getCaption())
                .mimeType(video.getMimeType())
                .width(video.getWidth())
                .height(video.getHeight())
                .seconds(video.getSeconds())
                .viewOnce(video.getViewOnce())
                .isGif(video.getIsGif())
                .isStatusReply(video.getIsStatusReply())
                .isEdit(video.getIsEdit())
                .isGroup(video.getIsGroup())
                .isNewsletter(video.getIsNewsletter())
                .forwarded(video.getForwarded())
                .chatName(video.getChatName())
                .senderName(video.getSenderName())
                .status(video.getStatus())
                .savedInGallery(video.getSavedInGallery())
                .build();
    }
}