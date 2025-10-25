package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.AudioDTO;
import com.example.loginauthapi.entities.Audio;
import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.repositories.AudioRepository;
import com.example.loginauthapi.repositories.ChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AudioService {

    private final AudioRepository audioRepository;
    private final ChatRepository chatRepository;

    /**
     * Salvar áudio recebido via webhook
     */
    @Transactional
    public Audio saveIncomingAudio(String chatId, String messageId, String instanceId,
                                   String connectedPhone, String phone, Boolean fromMe,
                                   Long timestamp, Integer seconds, String audioUrl,
                                   String mimeType, Boolean viewOnce, Boolean isStatusReply,
                                   String senderName, String senderPhoto, String status) {

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        // Verificar se o áudio já existe
        Optional<Audio> existing = audioRepository.findByMessageId(messageId);
        if (existing.isPresent()) {
            log.info("Áudio {} já existe no banco", messageId);
            return existing.get();
        }

        Audio audio = new Audio();
        audio.setChat(chat);
        audio.setMessageId(messageId);
        audio.setInstanceId(instanceId);
        audio.setConnectedPhone(connectedPhone);
        audio.setPhone(phone);
        audio.setFromMe(fromMe);
        audio.setTimestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
        audio.setSeconds(seconds);
        audio.setAudioUrl(audioUrl);
        audio.setMimeType(mimeType);
        audio.setViewOnce(viewOnce != null ? viewOnce : false);
        audio.setIsStatusReply(isStatusReply != null ? isStatusReply : false);
        audio.setSenderName(senderName);
        audio.setSenderPhoto(senderPhoto);
        audio.setStatus(status);

        Audio saved = audioRepository.save(audio);
        log.info("✅ Áudio salvo - MessageId: {}, ChatId: {}", messageId, chatId);

        return saved;
    }

    /**
     * Salvar áudio sendo enviado (antes de enviar via Z-API)
     */
    @Transactional
    public AudioDTO saveOutgoingAudio(String chatId, String phone, Integer duration, String audioUrl) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        Audio audio = new Audio();
        audio.setChat(chat);
        audio.setMessageId("temp-" + System.currentTimeMillis());
        audio.setInstanceId("local");
        // ✅ CORRIGIDO: Acessar connectedPhone através do WebInstance
        audio.setConnectedPhone(chat.getWebInstance().getConnectedPhone());
        audio.setPhone(phone);
        audio.setFromMe(true);
        audio.setTimestamp(LocalDateTime.now());
        audio.setSeconds(duration);
        audio.setAudioUrl(audioUrl);
        audio.setMimeType("audio/ogg; codecs=opus");
        audio.setViewOnce(false);
        audio.setIsStatusReply(false);
        audio.setStatus("PENDING");

        Audio saved = audioRepository.save(audio);
        log.info("✅ Áudio outgoing salvo temporariamente - TempMessageId: {}", saved.getMessageId());

        return convertToDTO(saved);
    }

    /**
     * Atualizar messageId após envio via Z-API
     */
    @Transactional
    public void updateAudioIdAfterSend(String tempMessageId, String realMessageId, String status) {
        Optional<Audio> audioOpt = audioRepository.findByMessageId(tempMessageId);
        if (audioOpt.isPresent()) {
            Audio audio = audioOpt.get();
            audio.setMessageId(realMessageId);
            audio.setStatus(status);
            audioRepository.save(audio);
            log.info("✅ AudioId atualizado: {} -> {}", tempMessageId, realMessageId);
        }
    }

    /**
     * Buscar áudios de um chat
     */
    public List<AudioDTO> getAudiosByChatId(String chatId) {
        if (!chatRepository.existsById(chatId)) {
            throw new RuntimeException("Chat não encontrado");
        }

        List<Audio> audios = audioRepository.findByChatIdOrderByTimestampAsc(chatId);

        return audios.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Converter para DTO
     */
    private AudioDTO convertToDTO(Audio audio) {
        return AudioDTO.builder()
                .id(audio.getId())
                .messageId(audio.getMessageId())
                .instanceId(audio.getInstanceId())
                .connectedPhone(audio.getConnectedPhone())
                .phone(audio.getPhone())
                .fromMe(audio.getFromMe())
                .timestamp(audio.getTimestamp().toString())
                .seconds(audio.getSeconds())
                .audioUrl(audio.getAudioUrl())
                .mimeType(audio.getMimeType())
                .viewOnce(audio.getViewOnce())
                .isStatusReply(audio.getIsStatusReply())
                .senderName(audio.getSenderName())
                .senderPhoto(audio.getSenderPhoto())
                .status(audio.getStatus())
                .build();
    }
}