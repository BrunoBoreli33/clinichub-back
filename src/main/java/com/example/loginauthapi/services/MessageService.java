package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.MessageDTO;
import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.Message;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.MessageRepository;
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
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;

    /**
     * Salvar mensagem recebida via webhook
     */
    @Transactional
    public Message saveIncomingMessage(String chatId, String messageId, String content,
                                       Boolean fromMe, Long timestamp, String status,
                                       String senderName, String senderPhoto,
                                       Boolean isForwarded, Boolean isGroup) {

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        // Verificar se a mensagem já existe
        Optional<Message> existing = messageRepository.findByMessageId(messageId);
        if (existing.isPresent()) {
            log.info("Mensagem {} já existe no banco", messageId);
            return existing.get();
        }

        Message message = new Message();
        message.setChat(chat);
        message.setMessageId(messageId);
        message.setContent(content);
        message.setFromMe(fromMe);
        message.setTimestamp(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
        ));
        message.setStatus(status);
        message.setSenderName(senderName);
        message.setSenderPhoto(senderPhoto);
        message.setIsForwarded(isForwarded);
        message.setIsGroup(isGroup);
        message.setIsEdited(false);

        Message saved = messageRepository.save(message);
        log.info("Mensagem {} salva com sucesso para chat {}", messageId, chatId);

        return saved;
    }

    /**
     * ✅ NOVO: Salvar mensagem ANTES de enviar via Z-API (optimistic save)
     */
    @Transactional
    public MessageDTO saveOutgoingMessage(String chatId, String content, User user) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        // Validar que o chat pertence ao usuário
        if (!chat.getWebInstance().getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Chat não pertence ao usuário");
        }

        // Gerar ID temporário para a mensagem (será substituído pelo ID do WhatsApp)
        String tempMessageId = "temp_" + System.currentTimeMillis();

        Message message = new Message();
        message.setChat(chat);
        message.setMessageId(tempMessageId);
        message.setContent(content);
        message.setFromMe(true);
        message.setTimestamp(LocalDateTime.now());
        message.setStatus("SENDING");
        message.setSenderName(user.getName());
        message.setSenderPhoto(null);
        message.setIsForwarded(false);
        message.setIsGroup(false);
        message.setIsEdited(false);

        Message saved = messageRepository.save(message);
        log.info("✅ Mensagem salva antes de enviar - TempId: {}, ChatId: {}", tempMessageId, chatId);

        return convertToDTO(saved);
    }

    /**
     * ✅ NOVO: Salvar mensagem de áudio ANTES de enviar
     */
    @Transactional
    public MessageDTO saveOutgoingAudioMessage(String chatId, String audioBase64, Integer duration, User user) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        if (!chat.getWebInstance().getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Chat não pertence ao usuário");
        }

        String tempMessageId = "temp_audio_" + System.currentTimeMillis();

        Message message = new Message();
        message.setChat(chat);
        message.setMessageId(tempMessageId);
        message.setContent("🎤 Áudio"); // Texto alternativo
        message.setType("audio");
        message.setAudioUrl(audioBase64); // Salvar base64 temporariamente
        message.setAudioDuration(duration);
        message.setFromMe(true);
        message.setTimestamp(LocalDateTime.now());
        message.setStatus("SENDING");
        message.setSenderName(user.getName());
        message.setSenderPhoto(null);
        message.setIsForwarded(false);
        message.setIsGroup(false);
        message.setIsEdited(false);

        Message saved = messageRepository.save(message);
        log.info("✅ Mensagem de áudio salva antes de enviar - TempId: {}", tempMessageId);

        return convertToDTO(saved);
    }

    /**
     * ✅ NOVO: Atualizar messageId após envio via Z-API
     */
    @Transactional
    public void updateMessageIdAfterSend(String tempMessageId, String realMessageId, String status) {
        Optional<Message> messageOpt = messageRepository.findByMessageId(tempMessageId);

        if (messageOpt.isPresent()) {
            Message message = messageOpt.get();
            message.setMessageId(realMessageId);
            message.setStatus(status);
            messageRepository.save(message);
            log.info("✅ MessageId atualizado - Temp: {} → Real: {}", tempMessageId, realMessageId);
        } else {
            log.warn("⚠️ Mensagem temporária não encontrada: {}", tempMessageId);
        }
    }

    /**
     * Buscar mensagens de um chat
     */
    public List<MessageDTO> getMessagesByChatId(String chatId, User user) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        if (!chat.getWebInstance().getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Chat não pertence ao usuário");
        }

        List<Message> messages = messageRepository.findByChatIdOrderByTimestampAsc(chatId);

        return messages.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Editar mensagem
     */
    @Transactional
    public Message editMessage(String messageId, String newContent) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new RuntimeException("Mensagem não encontrada"));

        message.setContent(newContent);
        message.setIsEdited(true);

        return messageRepository.save(message);
    }

    /**
     * Limpar mensagens antigas (mais de 60 dias)
     */
    @Transactional
    public void cleanOldMessages() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(60);
        messageRepository.deleteOldMessages(cutoffDate);
        log.info("Mensagens anteriores a {} foram deletadas", cutoffDate);
    }

    /**
     * ✅ MODIFICADO: Converter para DTO incluindo campos de áudio
     */
    private MessageDTO convertToDTO(Message message) {
        return MessageDTO.builder()
                .id(message.getId())
                .messageId(message.getMessageId())
                .content(message.getContent())
                .type(message.getType()) // ✅ NOVO
                .audioUrl(message.getAudioUrl()) // ✅ NOVO
                .audioDuration(message.getAudioDuration()) // ✅ NOVO
                .fromMe(message.getFromMe())
                .timestamp(message.getTimestamp().toString())
                .status(message.getStatus())
                .senderName(message.getSenderName())
                .senderPhoto(message.getSenderPhoto())
                .isEdited(message.getIsEdited())
                .isForwarded(message.getIsForwarded())
                .build();
    }
}