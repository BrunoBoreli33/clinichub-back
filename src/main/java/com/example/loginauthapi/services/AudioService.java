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
     * ✅ MODIFICADO: Salvar áudio recebido via webhook
     * - Agora busca pelo messageId PRIMEIRO antes de qualquer outra lógica
     * - Se encontrar por messageId, atualiza (caso do áudio enviado)
     * - PRESERVA o seconds original (vindo do frontend) e não sobrescreve com o valor do webhook
     * - Se não encontrar, cria novo áudio normalmente
     */
    @Transactional
    public Audio saveIncomingAudio(String chatId, String messageId, String instanceId,
                                   String connectedPhone, String phone, Boolean fromMe,
                                   Long timestamp, Integer seconds, String audioUrl,
                                   String mimeType, Boolean viewOnce, Boolean isStatusReply,
                                   String senderName, String senderPhoto, String status) {

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        log.info("🎤 saveIncomingAudio - ChatId: {}, MessageId: {}, FromMe: {}, Seconds: {}, AudioUrl: {}",
                chatId, messageId, fromMe, seconds, audioUrl != null ? "presente" : "null");

        // ✅ MODIFICADO: Primeiro, verificar se o áudio já existe pelo messageId
        Optional<Audio> existing = audioRepository.findByMessageId(messageId);

        if (existing.isPresent()) {
            Audio audio = existing.get();

            log.info("🔄 Áudio encontrado pelo messageId, atualizando com dados do webhook");
            log.info("   MessageId: {}", messageId);
            log.info("   AudioUrl anterior: {}", audio.getAudioUrl());
            log.info("   AudioUrl novo: {}", audioUrl);
            log.info("   Seconds ORIGINAL (frontend): {} - MANTIDO", audio.getSeconds());
            log.info("   Seconds webhook (ignorado): {}", seconds);
            log.info("   Status anterior: {}", audio.getStatus());
            log.info("   Status novo: {}", status);

            // Atualizar com dados do webhook
            audio.setInstanceId(instanceId);
            audio.setTimestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
            // ✅ NÃO sobrescrever seconds - manter o valor original do frontend
            // audio.setSeconds(seconds != null ? seconds : audio.getSeconds()); // REMOVIDO
            audio.setAudioUrl(audioUrl != null && !audioUrl.isEmpty() ? audioUrl : audio.getAudioUrl());
            audio.setMimeType(mimeType);
            audio.setSenderName(senderName);
            audio.setSenderPhoto(senderPhoto);
            audio.setStatus(status != null ? status : audio.getStatus());

            Audio updated = audioRepository.save(audio);

            log.info("✅ Áudio atualizado com sucesso!");
            log.info("   AudioUrl final: {}", updated.getAudioUrl());
            log.info("   Seconds final (do frontend): {}", updated.getSeconds());
            log.info("   Status final: {}", updated.getStatus());

            return updated;
        }

        // Se não encontrou por messageId, criar novo áudio
        log.info("📝 Criando novo áudio - MessageId: {}, FromMe: {}", messageId, fromMe);

        Audio audio = new Audio();
        audio.setChat(chat);
        audio.setMessageId(messageId);
        audio.setInstanceId(instanceId);
        audio.setConnectedPhone(connectedPhone);
        audio.setPhone(phone);
        audio.setFromMe(fromMe);
        audio.setTimestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
        audio.setSeconds(seconds != null ? seconds : 0);
        audio.setAudioUrl(audioUrl != null ? audioUrl : "");
        audio.setMimeType(mimeType);
        audio.setViewOnce(viewOnce != null ? viewOnce : false);
        audio.setIsStatusReply(isStatusReply != null ? isStatusReply : false);
        audio.setSenderName(senderName);
        audio.setSenderPhoto(senderPhoto);
        audio.setStatus(status != null ? status : "PENDING");

        Audio saved = audioRepository.save(audio);
        log.info("✅ Áudio criado - MessageId: {}, ChatId: {}, Seconds: {}, AudioUrl: {}",
                messageId, chatId, saved.getSeconds(), saved.getAudioUrl());

        return saved;
    }

    /**
     * ✅ MODIFICADO: Salvar áudio sendo enviado (antes de enviar via Z-API)
     * Agora também atualiza o chat com lastMessageContent, lastMessageTime e zera unread
     */
    @Transactional
    public AudioDTO saveOutgoingAudio(String chatId, String phone, Integer duration, String audioUrl) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        log.info("💾 Salvando áudio PENDING - ChatId: {}, Duration: {}", chatId, duration);

        Audio audio = new Audio();
        audio.setChat(chat);
        audio.setMessageId("temp-" + System.currentTimeMillis());
        audio.setInstanceId("local");
        audio.setConnectedPhone(chat.getWebInstance().getConnectedPhone());
        audio.setPhone(phone);
        audio.setFromMe(true);
        audio.setTimestamp(LocalDateTime.now());
        audio.setSeconds(duration != null ? duration : 0);
        audio.setAudioUrl(audioUrl != null ? audioUrl : "");
        audio.setMimeType("audio/ogg; codecs=opus");
        audio.setViewOnce(false);
        audio.setIsStatusReply(false);
        audio.setStatus("PENDING");

        Audio saved = audioRepository.save(audio);

        // ✅ NOVO: Atualizar o chat com lastMessageContent, lastMessageTime e zerar unread
        chat.setLastMessageContent("Mensagem de Áudio");
        chat.setLastMessageTime(LocalDateTime.now());
        chat.setUnread(0);
        chatRepository.save(chat);

        log.info("✅ Áudio PENDING salvo - TempMessageId: {}, Duration: {}", saved.getMessageId(), saved.getSeconds());
        log.info("✅ Chat atualizado - LastMessage: '{}', Unread: 0", chat.getLastMessageContent());

        return convertToDTO(saved);
    }

    /**
     * ✅ MODIFICADO: Atualizar messageId após envio via Z-API
     * NÃO atualiza o status - mantém como PENDING para que o webhook possa atualizar
     */
    @Transactional
    public void updateAudioIdAfterSend(String tempMessageId, String realMessageId, String status) {
        Optional<Audio> audioOpt = audioRepository.findByMessageId(tempMessageId);
        if (audioOpt.isPresent()) {
            Audio audio = audioOpt.get();
            audio.setMessageId(realMessageId);
            // ✅ NÃO atualizar o status aqui - manter PENDING para o webhook atualizar
            // O webhook irá atualizar com o status correto E o audioUrl
            audioRepository.save(audio);
            log.info("✅ AudioId atualizado: {} -> {} (Status mantido como PENDING para webhook)",
                    tempMessageId, realMessageId);
        } else {
            log.warn("⚠️ Áudio temporário não encontrado: {}", tempMessageId);
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
     * âœ… NOVO: Excluir Audio do banco de dados
     */
    @Transactional
    public void deleteAudio(String messageId) {
        try {
            log.info("ðŸ—'ï¸ Excluindo Ã¡udio - MessageId: {}", messageId);

            Optional<Audio> audioOpt = audioRepository.findByMessageId(messageId);

            if (audioOpt.isPresent()) {
                audioRepository.delete(audioOpt.get());
                log.info("Audio Excluido do Banco - MessageId: {}", messageId);
            } else {
                log.warn("Audio não encontrado no banco - MessageId: {}", messageId);
            }

        } catch (Exception e) {
            log.error("Erro ao Excluir Audio do Banco!", e);
            throw new RuntimeException("Erro ao excluir Ã¡udio: " + e.getMessage(), e);
        }
    }

    /**
     * Buscar áudio por messageId
     */
    public Optional<Audio> findByMessageId(String messageId) {
        return audioRepository.findByMessageId(messageId);
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